package com.example.mocktool.services;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bieco.common.enums.BiecoToolStatuses;

import java.util.Hashtable;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class AppRegistryService {
    private String orchestratorToken = "rK6ILPiurtfFV";
    private String orchestratorUrl = "http://localhost:4000/orchestrator/biecointerface";

    private String token = "JAD7A6WAD454AS33AF4";
    private String state = BiecoToolStatuses.ONLINE;
    private String toolId = "";
    private String jobId = "-1";
    private Map<String, String> params = new Hashtable<>();

    public void addParam(String key, String value) {
        params.put(key, value);
    }

    public String getParam(String key) {
        return params.get(key);
    }
}
