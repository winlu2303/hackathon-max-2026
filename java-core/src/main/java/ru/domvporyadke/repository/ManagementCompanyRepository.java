package ru.domvporyadke.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.domvporyadke.domain.ManagementCompany;

import java.util.List;

public interface ManagementCompanyRepository extends JpaRepository<ManagementCompany, Long> {

    @Query(value = """
        SELECT m.* FROM management_companies m
        WHERE m.address IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
            FROM unnest(string_to_array(:query, ' ')) AS t(token)
            WHERE length(t.token) >= 3
              AND position(
                    t.token
                    in lower(
                         coalesce(m.region, '') || ' ' ||
                         coalesce(m.city, '')   || ' ' ||
                         coalesce(m.address, '')
                       )
                  ) = 0
          )
        ORDER BY length(coalesce(m.address, '')) ASC
        LIMIT 20
    """, nativeQuery = true)
    List<ManagementCompany> searchByAddressTokens(@Param("query") String query);

    List<ManagementCompany> findByAddressPatternContainingIgnoreCase(String part);
}