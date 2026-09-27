package ru.domvporyadke.max;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import ru.domvporyadke.domain.EmergencyAlert;
import ru.domvporyadke.domain.ManagementCompany;
import ru.domvporyadke.domain.ServiceRequest;
import ru.domvporyadke.repository.ManagementCompanyRepository;
import ru.domvporyadke.service.EmergencyService;
import ru.domvporyadke.service.MaxUserService;
import ru.domvporyadke.service.ServiceRequestService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {

    private final MAXApiClient maxApiClient;
    private final FSMService fsmService;
    private final ManagementCompanyRepository ukRepo;
    private final MaxUserService maxUserService;
    private final EmergencyService emergencyService;
    private final ServiceRequestService serviceRequestService;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

    private static final Map<String, String> EMERGENCY_LABELS = Map.of(
            "flood",   "🌊 Потоп",
            "fire",    "🔥 Дым / проводка",
            "power",   "⚡ Выбило пробки",
            "blocked", "🚪 Заблокирован",
            "gas",     "💨 Утечка газа"
    );

    private static final Map<String, String> MASTER_LABELS = Map.of(
            "handyman",  "🔧 Мастер на все руки",
            "electric",  "⚡ Электрик",
            "plumber",   "🚰 Сантехник",
            "furniture", "🪑 Сборщик мебели",
            "carpenter", "🚪 Плотник / столяр"
    );

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "NEW",             "📥 Заявка принята ботом",
            "SENT_TO_UK",      "📤 Заявка отправлена в УК",
            "IN_PROGRESS",     "🔧 Заявка в работе в УК",
            "PENDING_CONFIRM", "⏳ Заявка отработана, ждёт подтверждения",
            "CLOSED",          "✅ Заявка завершена"
    );

    public void handle(JsonNode update) {
        try {
            log.info("RAW UPDATE: {}", update.toString());
            String type = update.path("update_type").asText();

            if (!"message_created".equals(type)
                    && !"bot_started".equals(type)
                    && !"message_callback".equals(type)) {
                return;
            }

            long chatId = findChatId(update);
            if (chatId == 0) {
                log.error("Cannot determine chatId from update: {}", update);
                return;
            }

            String incomingText = update.path("message").path("body").path("text").asText("");
            boolean isBotStarted = "bot_started".equals(type);

            if ("message_created".equals(type) && incomingText.isBlank()) {
                return;
            }

            if (isBotStarted) {
                log.info("bot_started from chat {} — full reset", chatId);
                fsmService.clearAll(chatId);
                handleRegistration(chatId, update, type);
                return;
            }

            if (incomingText.startsWith("/start") && !"message_callback".equals(type)) {
                fsmService.clearState(chatId);
                if (fsmService.hasProfile(chatId)) {
                    sendMainMenu(chatId);
                } else {
                    handleRegistration(chatId, update, type);
                }
                return;
            }

            if ("message_callback".equals(type)) {
                String payload = update.path("callback").path("payload").asText("");
                log.info("Callback from chat {}: {} (state={})",
                        chatId, payload, fsmService.getState(chatId));

                if (payload.startsWith("role_")
                        || payload.startsWith("uk_")
                        || "addr_confirm".equals(payload)
                        || "addr_change".equals(payload)
                        || "private_sector".equals(payload)) {
                    handleRegistration(chatId, update, type);
                    return;
                }
                handleCallback(chatId, payload);
                return;
            }

            String state = fsmService.getState(chatId);

            if ("ENTER_ADDRESS".equals(state)) {
                handleRegistration(chatId, update, type);
                return;
            }

            if (!fsmService.hasProfile(chatId)) {
                handleRegistration(chatId, update, type);
                return;
            }

            String text = incomingText;
            log.info("Message from chat {}: {} (state={})", chatId, text, state);

            if ("ENTER_APARTMENT".equals(state)) {
                fsmService.setTemp(chatId, "apartment", text);
                String flow = fsmService.getTemp(chatId, "flow");
                fsmService.setState(chatId, "CHOOSE_CATEGORY");
                if ("emergency".equals(flow)) {
                    sendEmergencyMenu(chatId);
                } else {
                    sendMasterMenu(chatId);
                }
                return;
            }

            if ("ENTER_NAME".equals(state)) {
                String flow = fsmService.getTemp(chatId, "flow");
                Long lastId = fsmService.getLastRequest(chatId, flow);
                if (lastId != null) {
                    if ("emergency".equals(flow)) {
                        emergencyService.updateCaller(lastId, null, text);
                    } else {
                        serviceRequestService.updateCaller(lastId, null, text);
                    }
                }
                fsmService.clearState(chatId);
                maxApiClient.sendSimpleMessage(chatId,
                        "Ваше заявление было отправлено диспетчеру. " +
                        "Просим соблюдать спокойствие. С Вами свяжутся в ближайшее время.");
                sendMainMenu(chatId);
                return;
            }

            if ("ENTER_COMPLAINT".equals(state)) {
                Long ukId = maxUserService.getUkId(chatId);
                fsmService.clearState(chatId);

                if (ukId == null) {
                    log.info("Complaint from chat {} (private sector): {}", chatId, text);
                    maxApiClient.sendSimpleMessage(chatId,
                            "У Вас не привязана управляющая компания (частный сектор).\n" +
                            "Жалобу можно подать в местную администрацию или через " +
                            "«Госуслуги. Решаем вместе».\n\n" +
                            "Текст сохранён — при привязке УК жалоба будет передана.");
                } else {
                    ManagementCompany uk = ukRepo.findById(ukId).orElse(null);
                    String ukName = (uk == null) ? "—" : safe(uk.getName());
                    log.info("Complaint from chat {} to UK {}: {}", chatId, ukName, text);
                    maxApiClient.sendSimpleMessage(chatId,
                            "Жалоба принята и будет передана в УК «" + ukName + "».\n" +
                            "Мы свяжемся с Вами в ближайшее время.");
                }
                sendMainMenu(chatId);
                return;
            }

            if (text.contains("АВАРИЯ") || text.contains("авария")) {
                sendEmergencyMenu(chatId);
                return;
            }
            if (text.contains("мастер") || text.contains("Мастер")) {
                sendMasterMenu(chatId);
                return;
            }
            if (text.contains("заявк") || text.contains("Заявк")) {
                sendMyRequests(chatId);
                return;
            }
            maxApiClient.sendSimpleMessage(chatId, "Не понял команду. Нажмите /start");
        } catch (Exception e) {
            log.error("MessageHandler failed on update: {}", update, e);
        }
    }

    private long findChatId(JsonNode update) {
        long[] candidates = {
            update.path("message").path("recipient").path("chat_id").asLong(),
            update.path("chat_id").asLong(),
            update.path("callback").path("message").path("recipient").path("chat_id").asLong(),
            update.path("recipient").path("chat_id").asLong()
        };
        for (long id : candidates) if (id != 0) return id;
        return 0;
    }

    private void handleCallback(long chatId, String payload) {
        if (payload.startsWith("close_emergency_")) {
            Long id = parseId(payload, "close_emergency_");
            askCloseConfirm(chatId, "emergency", id);
            return;
        }
        if (payload.startsWith("close_master_")) {
            Long id = parseId(payload, "close_master_");
            askCloseConfirm(chatId, "master", id);
            return;
        }
        if (payload.startsWith("confirm_close_emergency_")) {
            Long id = parseId(payload, "confirm_close_emergency_");
            doCloseForce(chatId, "emergency", id);
            return;
        }
        if (payload.startsWith("confirm_close_master_")) {
            Long id = parseId(payload, "confirm_close_master_");
            doCloseForce(chatId, "master", id);
            return;
        }
        if ("cancel_close".equals(payload)) {
            maxApiClient.sendSimpleMessage(chatId, "Отменено.");
            sendMyRequests(chatId);
            return;
        }

        switch (payload) {
            case "emergency_start" -> {
                fsmService.setTemp(chatId, "flow", "emergency");
                startApartmentOrCategory(chatId, "emergency");
            }
            case "master_start" -> {
                fsmService.setTemp(chatId, "flow", "master");
                startApartmentOrCategory(chatId, "master");
            }
            case "master_info" -> sendMasterInfo(chatId);
            case "my_requests" -> sendMyRequests(chatId);
            case "back_to_main" -> sendMainMenu(chatId);
            case "show_uk_contacts" -> sendUkContacts(chatId);
            case "emergency_contacts" -> sendEmergencyContacts(chatId);

            case "change_address" -> {
                String role = fsmService.getProfile(chatId, "role");
                if (role == null || role.isBlank()) role = "OWNER";
                fsmService.setTemp(chatId, "role", role);
                fsmService.setState(chatId, "ENTER_ADDRESS");
                maxApiClient.sendSimpleMessage(chatId,
                        "Введите новый адрес (город, улица, дом):");
            }

            case "reset_all" -> {
                fsmService.clearAll(chatId);
                maxApiClient.sendSimpleMessage(chatId,
                        "Профиль сброшен. Начинаем заново.");
                handleRegistration(chatId,
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                        "message_created");
            }

            case "all_requests_status" -> sendAllRequestsStatus(chatId);

            case "complaint_start" -> {
                fsmService.setState(chatId, "ENTER_COMPLAINT");
                maxApiClient.sendSimpleMessage(chatId,
                        "Опишите вашу жалобу в УК одним сообщением:");
            }

            case "new_request" -> {
                fsmService.clearState(chatId);
                sendMainMenu(chatId);
            }

            case "skip_name" -> {
                fsmService.clearState(chatId);
                maxApiClient.sendSimpleMessage(chatId,
                        "Хорошо. Заявка принята, с Вами свяжутся в ближайшее время.");
                sendMainMenu(chatId);
            }

            case "emergency_flood", "emergency_fire",
                 "emergency_power", "emergency_blocked", "emergency_gas" ->
                    createEmergency(chatId, payload);

            case "master_handyman", "master_electric", "master_plumber",
                 "master_furniture", "master_carpenter" ->
                    createServiceRequest(chatId, payload);

            case "emergency_send_uk" -> {
                maxApiClient.sendSimpleMessage(chatId, "Заявка отправлена в УК.");
                sendMainMenu(chatId);
            }
            case "call_water", "call_gas", "call_power", "call_112" ->
                    maxApiClient.sendSimpleMessage(chatId,
                            "☎️ Позвоните по номеру: 112 (или 04 для газа)");

            default -> {
                log.warn("Unknown callback payload: {}", payload);
                maxApiClient.sendSimpleMessage(chatId, "Не понял кнопку. /start");
            }
        }
    }

    private void startApartmentOrCategory(long chatId, String flow) {
        Long ukId = maxUserService.getUkId(chatId);
        if (ukId == null) {
            fsmService.setState(chatId, "CHOOSE_CATEGORY");
            if ("emergency".equals(flow)) {
                sendEmergencyMenu(chatId);
            } else {
                sendMasterMenu(chatId);
            }
        } else {
            fsmService.setState(chatId, "ENTER_APARTMENT");
            maxApiClient.sendSimpleMessage(chatId, "Укажите номер квартиры:");
        }
    }

    private Long parseId(String payload, String prefix) {
        try {
            return Long.parseLong(payload.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void askCloseConfirm(long chatId, String kind, Long id) {
        if (id == null) {
            maxApiClient.sendSimpleMessage(chatId, "Некорректный id заявки.");
            return;
        }
        String label = "emergency".equals(kind) ? "аварийную" : "заявку на мастера";
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("✅ Да, завершить", "confirm_close_" + kind + "_" + id),
                MAXApiClient.row("❌ Отмена", "cancel_close"),
                MAXApiClient.row("⬅️ Назад", "my_requests")
        );
        maxApiClient.sendMessage(chatId,
                "Вы уверены, что хотите завершить " + label + " #" + id + " принудительно?",
                rows);
    }

    private void doCloseForce(long chatId, String kind, Long id) {
        if (id == null) {
            maxApiClient.sendSimpleMessage(chatId, "Некорректный id заявки.");
            return;
        }
        try {
            if ("emergency".equals(kind)) {
                emergencyService.closeForce(id);
            } else {
                serviceRequestService.closeForce(id);
            }
            maxApiClient.sendSimpleMessage(chatId, "Заявка #" + id + " завершена.");
        } catch (Exception e) {
            log.error("closeForce failed for {} #{}", kind, id, e);
            maxApiClient.sendSimpleMessage(chatId, "Не удалось завершить заявку #" + id);
        }
        sendMyRequests(chatId);
    }

    private void createEmergency(long chatId, String payload) {
        String apartment = fsmService.getTemp(chatId, "apartment");
        String address = fsmService.getProfile(chatId, "address");
        Long userId = maxUserService.getLinkedUserId(chatId);
        Long ukId = maxUserService.getUkId(chatId);
        String type = payload.substring("emergency_".length());

        EmergencyAlert e = emergencyService.create(
                userId, type, address, apartment, null, null, ukId);

        fsmService.setLastRequest(chatId, "emergency", e.getId());
        fsmService.setTemp(chatId, "flow_type", type);

        sendEmergencyActions(chatId, type);
        fsmService.setState(chatId, "ENTER_NAME");
    }

    private void createServiceRequest(long chatId, String payload) {
        String apartment = fsmService.getTemp(chatId, "apartment");
        String address = fsmService.getProfile(chatId, "address");
        Long userId = maxUserService.getLinkedUserId(chatId);
        Long ukId = maxUserService.getUkId(chatId);
        String category = payload.substring("master_".length());

        ServiceRequest r = serviceRequestService.create(
                userId, category, address, apartment, null, null, ukId);

        fsmService.setLastRequest(chatId, "master", r.getId());
        fsmService.setState(chatId, "ENTER_NAME");
        maxApiClient.sendSimpleMessage(chatId,
                "Уточните: как я могу к Вам обращаться?");
    }

    private void sendMyRequests(long chatId) {
        Long userId = maxUserService.getLinkedUserId(chatId);
        if (userId == null) {
            maxApiClient.sendSimpleMessage(chatId,
                    "Сначала завершите регистрацию (команда /start).");
            return;
        }
        List<EmergencyAlert> emergencies = emergencyService.findOpenByUserId(userId);
        List<ServiceRequest> requests = serviceRequestService.findOpenByUserId(userId);

        if (emergencies.isEmpty() && requests.isEmpty()) {
            maxApiClient.sendSimpleMessage(chatId,
                    "У вас нет открытых заявок.\n" +
                    "Посмотреть все — «📊 Статус всех заявок».");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 Ваши активные заявки:\n");
        List<List<Map<String, Object>>> closeButtons = new ArrayList<>();

        for (EmergencyAlert e : emergencies) {
            sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🚨 АВАРИЯ #").append(e.getId()).append("\n");
            sb.append("Причина: ").append(emergencyLabel(e.getType())).append("\n");
            sb.append("Статус: ").append(statusLabel(e.getStatus())).append("\n");
            sb.append("Адрес: ").append(formatAddress(e.getAddress(), e.getApartment())).append("\n");
            sb.append("Заявитель: ").append(safe(e.getCallerName())).append("\n");
            sb.append("Создана: ").append(formatInstant(e.getCreatedAt())).append("\n");
            sb.append(ukBlockFor(e.getUkId())).append("\n");

            closeButtons.add(MAXApiClient.row(
                    "✅ Завершить аварию #" + e.getId(),
                    "close_emergency_" + e.getId()));
        }

        for (ServiceRequest r : requests) {
            sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🔧 МАСТЕР #").append(r.getId()).append("\n");
            sb.append("Категория: ").append(masterLabel(r.getCategory())).append("\n");
            sb.append("Статус: ").append(statusLabel(r.getStatus())).append("\n");
            sb.append("Адрес: ").append(formatAddress(r.getAddress(), r.getApartment())).append("\n");
            sb.append("Заявитель: ").append(safe(r.getCallerName())).append("\n");
            sb.append("Создана: ").append(formatInstant(r.getCreatedAt())).append("\n");
            sb.append(ukBlockFor(r.getUkId())).append("\n");

            closeButtons.add(MAXApiClient.row(
                    "✅ Завершить заявку на мастера #" + r.getId(),
                    "close_master_" + r.getId()));
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━");
        closeButtons.add(MAXApiClient.row("⬅️ Назад", "back_to_main"));

        maxApiClient.sendMessage(chatId, sb.toString(), closeButtons);
    }

    private void sendAllRequestsStatus(long chatId) {
        Long userId = maxUserService.getLinkedUserId(chatId);
        if (userId == null) {
            maxApiClient.sendSimpleMessage(chatId,
                    "Сначала завершите регистрацию (команда /start).");
            return;
        }
        List<EmergencyAlert> emergencies = emergencyService.findByUserId(userId);
        List<ServiceRequest> requests = serviceRequestService.findByUserId(userId);

        if (emergencies.isEmpty() && requests.isEmpty()) {
            maxApiClient.sendSimpleMessage(chatId, "Заявок нет.");
            return;
        }

        StringBuilder sb = new StringBuilder("📊 Статус всех ваших заявок:\n\n");
        for (EmergencyAlert e : emergencies) {
            sb.append(isClosed(e.getStatus()) ? "✅" : "⭕")
              .append(" 🚨 #").append(e.getId())
              .append(" ").append(emergencyLabel(e.getType()))
              .append(" — ").append(statusLabel(e.getStatus()))
              .append("\n");
        }
        for (ServiceRequest r : requests) {
            sb.append(isClosed(r.getStatus()) ? "✅" : "⭕")
              .append(" 🔧 #").append(r.getId())
              .append(" ").append(masterLabel(r.getCategory()))
              .append(" — ").append(statusLabel(r.getStatus()))
              .append("\n");
        }
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("➕ Создать новую заявку", "new_request"),
                MAXApiClient.row("⬅️ Назад", "back_to_main")
        );
        maxApiClient.sendMessage(chatId, sb.toString(), rows);
    }

    private static boolean isClosed(String status) {
        return "CLOSED".equals(status);
    }

    private String ukBlockFor(Long ukId) {
        if (ukId == null) return "УК: —";
        ManagementCompany uk = ukRepo.findById(ukId).orElse(null);
        return buildUkBlock(uk);
    }

    private static String buildUkBlock(ManagementCompany uk) {
        if (uk == null) return "УК: —";
        StringBuilder sb = new StringBuilder("УК: ").append(safe(uk.getName()));
        if (uk.getPhone() != null && !uk.getPhone().isBlank()) {
            sb.append("\n☎️ ").append(uk.getPhone());
        }
        return sb.toString();
    }

    private static String emergencyLabel(String type) {
        if (type == null) return "—";
        return EMERGENCY_LABELS.getOrDefault(type, type);
    }

    private static String masterLabel(String category) {
        if (category == null) return "—";
        return MASTER_LABELS.getOrDefault(category, category);
    }

    private static String statusLabel(String code) {
        if (code == null) return "—";
        return STATUS_LABELS.getOrDefault(code, code);
    }

    private static String formatAddress(String address, String apartment) {
        String a = (address == null || address.isBlank()) ? "—" : address;
        if (apartment != null && !apartment.isBlank()) {
            return a + ", кв. " + apartment;
        }
        return a;
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) return "—";
        return DT.format(instant);
    }

    private void sendUkContacts(long chatId) {
        Long ukId = maxUserService.getUkId(chatId);
        if (ukId == null) {
            maxApiClient.sendSimpleMessage(chatId,
                    "Сначала укажите адрес и УК через /start.");
            return;
        }
        ManagementCompany uk = ukRepo.findById(ukId).orElse(null);
        if (uk == null) {
            maxApiClient.sendSimpleMessage(chatId, "УК не найдена.");
            return;
        }
        String msg = "☎️ " + safe(uk.getName()) + "\n" +
                "Телефон: " + safe(uk.getPhone()) + "\n" +
                "Email: "   + safe(uk.getEmail()) + "\n" +
                "Сайт: "    + safe(uk.getWebsite()) + "\n" +
                "Часы: "    + safe(uk.getWorkingHours());
        maxApiClient.sendSimpleMessage(chatId, msg);
    }

    private void sendEmergencyContacts(long chatId) {
        String msg =
                "🚒 Экстренные службы\n\n" +
                "🚒 Служба спасения / пожарная: 112\n" +
                "💨 Диспетчер газа: 04 или 104\n" +
                "💧 Диспетчер воды: 112\n" +
                "⚡ Диспетчер электричества: 112\n" +
                "🚓 Полиция: 102\n" +
                "🚑 Скорая помощь: 103\n\n" +
                "Если произошла авария — сначала позвоните, потом оформите заявку через бота.";
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("📤 Оформить заявку в УК", "emergency_start"),
                MAXApiClient.row("⬅️ Назад", "back_to_main")
        );
        maxApiClient.sendMessage(chatId, msg, rows);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private void sendMainMenu(long chatId) {
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("🚨 АВАРИЯ", "emergency_start"),
                MAXApiClient.row("🔧 Вызвать мастера", "master_start"),
                MAXApiClient.row("📋 Мои активные заявки", "my_requests"),
                MAXApiClient.row("📊 Статус всех заявок", "all_requests_status"),
                MAXApiClient.row("📝 Подать жалобу в управляющую компанию", "complaint_start"),
                MAXApiClient.row("📍 Указать иной адрес для обращения к УК", "change_address"),
                MAXApiClient.row("☎️ Контакты управляющей компании", "show_uk_contacts"),
                MAXApiClient.row("🚒 Контакты Экстренных служб", "emergency_contacts"),
                MAXApiClient.row("🔄 Начать заново", "reset_all")
        );
        maxApiClient.sendMessage(chatId, "🏠 Дом в Порядке\nВыберите действие:", rows);
    }

    private void sendEmergencyMenu(long chatId) {
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("🌊 Потоп", "emergency_flood"),
                MAXApiClient.row("🔥 Дым / проводка", "emergency_fire"),
                MAXApiClient.row("⚡ Выбило пробки", "emergency_power"),
                MAXApiClient.row("🚪 Заблокирован вход/выход", "emergency_blocked"),
                MAXApiClient.row("💨 Утечка газа", "emergency_gas"),
                MAXApiClient.row("⬅️ Назад", "back_to_main")
        );
        maxApiClient.sendMessage(chatId, "🚨 АВАРИЯ\nЧто случилось?", rows);
    }

    private void sendEmergencyActions(long chatId, String type) {
        StringBuilder sb = new StringBuilder("🚨 Что делать прямо сейчас\n\n");
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        rows.add(MAXApiClient.row("📤 Отправить заявку в управляющую компанию", "emergency_send_uk"));

        switch (type == null ? "" : type) {
            case "flood" -> {
                sb.append("💧 Диспетчер воды: 112\n")
                  .append("Не отключайте электричество мокрыми руками, перекройте воду.\n\n");
                rows.add(MAXApiClient.row("💧 Диспетчер воды: 112", "call_water"));
            }
            case "gas" -> {
                sb.append("💨 Диспетчер газа: 04 или 104\n")
                  .append("Не включайте свет и не зажигайте огонь, откройте окна.\n\n");
                rows.add(MAXApiClient.row("💨 Диспетчер газа: 04 / 104", "call_gas"));
            }
            case "power", "fire" -> {
                sb.append("⚡ Диспетчер электричества: 112\n")
                  .append("Отключите вводной автомат, если это безопасно.\n\n");
                rows.add(MAXApiClient.row("⚡ Диспетчер электричества: 112", "call_power"));
            }
            case "blocked" -> {
                sb.append("🚪 Если заблокирован выход — вызывайте спасателей: 112\n\n");
            }
        }

        Long id = fsmService.getLastRequest(chatId, "emergency");
        sb.append("🚒 Служба спасения: 112\n\n")
          .append("Заявка №").append(id == null ? "?" : id)
          .append(" создана и будет передана диспетчеру.\n")
          .append("Уточните, как я могу к Вам обращаться? (или нажмите «Пропустить»)");

        rows.add(MAXApiClient.row("🚒 Служба спасения: 112", "call_112"));
        rows.add(MAXApiClient.row("✅ Пропустить имя", "skip_name"));

        maxApiClient.sendMessage(chatId, sb.toString(), rows);
    }

    private void sendMasterMenu(long chatId) {
        List<List<Map<String, Object>>> rows = List.of(
                MAXApiClient.row("🔧 Мастер на все руки", "master_handyman"),
                MAXApiClient.row("⚡ Электрик", "master_electric"),
                MAXApiClient.row("🚰 Сантехник", "master_plumber"),
                MAXApiClient.row("🪑 Сборщик мебели", "master_furniture"),
                MAXApiClient.row("🚪 Плотник / столяр", "master_carpenter"),
                MAXApiClient.row("📖 Описание категорий мастеров", "master_info"),
                MAXApiClient.row("⬅️ Назад", "back_to_main")
        );
        maxApiClient.sendMessage(chatId, "🔧 Вызов мастера\nВыберите категорию:", rows);
    }

    private void sendMasterInfo(long chatId) {
        String info =
                "📖 Описание категорий мастеров\n\n" +
                "🔧 Мастер на все руки (универсал)\n" +
                "Навеска карнизов, полок, картин, починка мебели, замена замков.\n\n" +
                "⚡ Электрик\n" +
                "Розетки, выключатели, люстры, светильники, мелкая проводка.\n\n" +
                "🚰 Сантехник\n" +
                "Протечки, смесители, сифоны, подключение стиральных и посудомоечных машин.\n\n" +
                "🪑 Сборщик мебели\n" +
                "Сборка шкафов, кроватей, кухонь, ремонт фурнитуры, петель, механизмов.\n\n" +
                "🚪 Плотник / столяр\n" +
                "Двери, наличники, плинтусы, напольные покрытия.";
        maxApiClient.sendSimpleMessage(chatId, info);
    }

    private void handleRegistration(long chatId, JsonNode update, String type) {
        String state = fsmService.getState(chatId);
        log.info("Registration state for chat {}: {}", chatId, state);

        String payload = "message_callback".equals(type)
                ? update.path("callback").path("payload").asText("")
                : "";
        String text = update.path("message").path("body").path("text").asText("");

        switch (state) {
            case "NEW" -> {
                List<List<Map<String, Object>>> rows = List.of(
                        MAXApiClient.row("🏠 Я собственник", "role_owner"),
                        MAXApiClient.row("🔑 Я арендатор", "role_tenant")
                );
                maxApiClient.sendMessage(chatId,
                        "Добро пожаловать в «Дом в Порядке»!\n\n" +
                        "Для начала работы укажите ваш статус проживания:", rows);
                fsmService.setState(chatId, "CHOOSE_ROLE");
            }
            case "CHOOSE_ROLE" -> {
                if ("role_owner".equals(payload)) {
                    fsmService.setTemp(chatId, "role", "OWNER");
                } else if ("role_tenant".equals(payload)) {
                    fsmService.setTemp(chatId, "role", "TENANT");
                } else {
                    maxApiClient.sendSimpleMessage(chatId, "Пожалуйста, выберите кнопку.");
                    return;
                }
                maxApiClient.sendSimpleMessage(chatId,
                        "Укажите адрес проживания (город, улица, дом):");
                fsmService.setState(chatId, "ENTER_ADDRESS");
            }
            case "ENTER_ADDRESS" -> {
                if (text.isBlank()) {
                    maxApiClient.sendSimpleMessage(chatId,
                            "Пожалуйста, введите адрес текстом.");
                    return;
                }
                if (text.startsWith("Адрес:")) {
                    maxApiClient.sendSimpleMessage(chatId,
                            "Введите адрес заново (город, улица, дом):");
                    return;
                }
                fsmService.setTemp(chatId, "address", text);
                List<List<Map<String, Object>>> rows = List.of(
                        MAXApiClient.row("✅ Да, верно", "addr_confirm"),
                        MAXApiClient.row("✏️ Изменить", "addr_change")
                );
                maxApiClient.sendMessage(chatId,
                        "Адрес: " + text + "\n\nВсё верно?", rows);
                fsmService.setState(chatId, "CONFIRM_ADDRESS");
            }
            case "CONFIRM_ADDRESS" -> {
                if ("addr_change".equals(payload)) {
                    maxApiClient.sendSimpleMessage(chatId, "Введите адрес заново:");
                    fsmService.setState(chatId, "ENTER_ADDRESS");
                    return;
                }
                if (!"addr_confirm".equals(payload)) {
                    maxApiClient.sendSimpleMessage(chatId,
                            "Нажмите «Да, верно» или «Изменить».");
                    return;
                }
                String address = fsmService.getTemp(chatId, "address");
                if (address == null || address.isBlank()) {
                    maxApiClient.sendSimpleMessage(chatId, "Введите адрес заново:");
                    fsmService.setState(chatId, "ENTER_ADDRESS");
                    return;
                }
                String normalized = normalizeAddressQuery(address);
                log.info("Address search: raw='{}' normalized='{}'", address, normalized);
                List<ManagementCompany> uks = ukRepo.searchByAddressTokens(normalized);
                log.info("Address search returned {} UK", uks.size());

                if (uks.isEmpty()) {
                    List<List<Map<String, Object>>> rows = List.of(
                            MAXApiClient.row("🏘️ Частный сектор", "private_sector"),
                            MAXApiClient.row("✏️ Изменить адрес", "addr_change")
                    );
                    maxApiClient.sendMessage(chatId,
                            "По адресу «" + address + "» УК не найдены.\n" +
                            "Если это частный сектор — нажмите кнопку ниже. " +
                            "Иначе попробуйте другой адрес (город, улица).", rows);
                    fsmService.setState(chatId, "NO_UK_FOUND");
                    return;
                }
                List<List<Map<String, Object>>> rows = new ArrayList<>();
                for (ManagementCompany uk : uks) {
                    rows.add(MAXApiClient.row("🏢 " + safe(uk.getName()),
                            "uk_" + uk.getId()));
                }
                rows.add(MAXApiClient.row("⬅️ Изменить адрес", "addr_change"));
                maxApiClient.sendMessage(chatId,
                        "По адресу «" + address + "» найдены УК:\nВыберите вашу:", rows);
                fsmService.setState(chatId, "CHOOSE_UK");
            }
            case "NO_UK_FOUND" -> {
                if ("private_sector".equals(payload)) {
                    String role = fsmService.getTemp(chatId, "role");
                    if (role == null || role.isBlank()) role = "OWNER";
                    String address = fsmService.getTemp(chatId, "address");

                    fsmService.saveProfile(chatId, role, address, null, null);
                    maxUserService.saveProfile(chatId, role, address, null, null);
                    Long internalUserId = maxUserService.getOrCreate(chatId).getId();
                    maxUserService.linkToUser(chatId, internalUserId);

                    fsmService.clearState(chatId);
                    maxApiClient.sendSimpleMessage(chatId,
                            "Профиль сохранён (частный сектор). УК не привязана.");
                    sendMainMenu(chatId);
                    return;
                }
                if ("addr_change".equals(payload)) {
                    maxApiClient.sendSimpleMessage(chatId, "Введите адрес заново:");
                    fsmService.setState(chatId, "ENTER_ADDRESS");
                    return;
                }
                maxApiClient.sendSimpleMessage(chatId,
                        "Нажмите «Частный сектор» или «Изменить адрес».");
            }
            case "CHOOSE_UK" -> {
                if (!payload.startsWith("uk_")) {
                    maxApiClient.sendSimpleMessage(chatId,
                            "Пожалуйста, выберите УК из списка.");
                    return;
                }
                long ukId;
                try {
                    ukId = Long.parseLong(payload.substring(3));
                } catch (NumberFormatException e) {
                    maxApiClient.sendSimpleMessage(chatId, "Некорректный выбор УК.");
                    return;
                }
                ManagementCompany uk = ukRepo.findById(ukId).orElse(null);
                if (uk == null) {
                    maxApiClient.sendSimpleMessage(chatId,
                            "УК не найдена, попробуйте снова.");
                    return;
                }
                String role = fsmService.getTemp(chatId, "role");
                if (role == null || role.isBlank()) role = "OWNER";
                String address = fsmService.getTemp(chatId, "address");
                Long buildingId = 1L;

                fsmService.saveProfile(chatId, role, address, buildingId, uk.getId());
                maxUserService.saveProfile(chatId, role, address, buildingId, uk.getId());
                Long internalUserId = maxUserService.getOrCreate(chatId).getId();
                maxUserService.linkToUser(chatId, internalUserId);

                fsmService.clearState(chatId);
                maxApiClient.sendSimpleMessage(chatId,
                        "Профиль сохранён.\n" +
                        "Статус: " + ("OWNER".equals(role) ? "собственник" : "арендатор") + "\n" +
                        "Адрес: " + address + "\n" +
                        "УК: " + safe(uk.getName()));
                sendMainMenu(chatId);
            }
            default -> {
                fsmService.clearState(chatId);
                fsmService.setState(chatId, "NEW");
                maxApiClient.sendSimpleMessage(chatId, "Начнём сначала. /start");
            }
        }
    }

    private static String normalizeAddressQuery(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}