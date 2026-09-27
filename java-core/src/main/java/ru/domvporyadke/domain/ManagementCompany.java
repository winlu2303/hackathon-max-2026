package ru.domvporyadke.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "management_companies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "inn")
    private String inn;

    @Column(name = "ogrn")
    private String ogrn;

    @Column(name = "address_pattern")
    private String addressPattern;

    @Column(name = "region")
    private String region;

    @Column(name = "city")
    private String city;

    @Column(name = "address")
    private String address; 

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "working_hours")
    private String workingHours;

    @Column(name = "max_link")
    private String maxLink;
}