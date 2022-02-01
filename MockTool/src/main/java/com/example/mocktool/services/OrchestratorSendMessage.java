package com.example.mocktool.services;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.*;
import org.bieco.common.models.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
@Getter
@Setter
public class OrchestratorSendMessage {
    Logger logger = LoggerFactory.getLogger(OrchestratorSendMessage.class);
    @Autowired
    private Gson gson;

    @Autowired
    private AppRegistryService appRegistryService;

    public String sendMessage(Message message) {
        MediaType mediaType = MediaType.parse("application/json");
        message.calculateCRC();
        RequestBody body = RequestBody.create(mediaType, gson.toJson(message));
        return this.sendRequest(body);
    }

    private String sendRequest(RequestBody body) {
        Request request = new Request.Builder()
                .url(appRegistryService.getOrchestratorUrl())
                .method("POST", body)
                .addHeader("Authorization", appRegistryService.getOrchestratorToken())
                .addHeader("Content-Type", "application/json")
                .build();
        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        Response response;
        try {
            response = call.execute();
            return response.body().string();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }
}
