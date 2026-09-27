package ru.domvporyadke.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.domvporyadke.domain.Building;
import ru.domvporyadke.repository.BuildingRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingsController {

    private final BuildingRepository repo;

    @GetMapping
    public List<Building> list() {
        return repo.findAll();
    }
}