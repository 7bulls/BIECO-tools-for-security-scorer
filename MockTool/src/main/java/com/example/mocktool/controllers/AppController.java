package com.example.mocktool.controllers;

import com.example.mocktool.services.AppRegistryService;
import com.example.mocktool.services.OrchestratorSendMessage;
import com.google.gson.Gson;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bieco.common.enums.BiecoMessageTypes;
import org.bieco.common.enums.BiecoToolStatuses;
import org.bieco.common.models.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/mock")
@NoArgsConstructor
public class AppController {
    private Message received;

    @Autowired
    private AppRegistryService appRegistryService;

    @Autowired
    private OrchestratorSendMessage sendMessage;

    @PostMapping("/biecointerface")
    public String processMessage(@org.springframework.web.bind.annotation.RequestBody Message message, @RequestHeader("Authorization") String token) {
        if (!appRegistryService.getToken().equals(token)) {
            System.out.println("Invalid token");
            System.out.println("token inside: " + appRegistryService.getToken());
            System.out.println("received token: " + token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access Forbidden\n");
        }
        validateMessage(message);
        if (!message.getMessageType().equals(BiecoMessageTypes.HEARTBEAT)) {
            System.out.println("Got message. S:" + appRegistryService.getState() +
                    " T:" + appRegistryService.getToolId() +
                    " J:" + appRegistryService.getJobId());
            System.out.println(message);
        }
        parseMessage(message);
        return null;
    }

    private void parseMessage(Message message) {
        this.received = message;
        switch (message.getMessageType()) {
            case BiecoMessageTypes.GETSTATUS:
                this.sendStatus();
                break;
            case BiecoMessageTypes.HEARTBEAT:
                this.heartbeat();
                break;
            case BiecoMessageTypes.CONFIGURE:
                this.configure();
                break;
            case BiecoMessageTypes.DATA:
                this.data();
                break;
            case BiecoMessageTypes.EVENT:
                this.event();
                break;
            case BiecoMessageTypes.START:
                this.start();
                break;
            case BiecoMessageTypes.STOP:
                this.stop();
                break;
            case BiecoMessageTypes.HALT:
                this.halt();
                break;
        }
    }

    private void halt() {
        System.out.println("Got HALT Message, halting...");
        new Thread() {
            @Override
            public void run() {
                Message ms;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                //send a finishing message with some data
                ms = getMessage(BiecoMessageTypes.HALTING);
                ms.setBody("{\"someKey\":\"someValue\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                //send finish message
                ms = getMessage(BiecoMessageTypes.HALTED);
                sendMessage.sendMessage(ms);
            }
        }.start();
    }

    private void stop() {
        System.out.println("Got Stop message, stopping...");
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                //send a finishing message with some data
                Message ms = getMessage(BiecoMessageTypes.FINISHING);
                ms.setBody("{\"someKey\":\"someValue\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                //send finish message
                ms = getMessage(BiecoMessageTypes.FINISHED);
                sendMessage.sendMessage(ms);
            }
        }.start();
    }

    private void start() {
        System.out.println("Got Start message, running");
        System.out.println("DEMO: RECEIVED START MESSAGE");
        appRegistryService.setState(BiecoToolStatuses.RUNNING);
        appRegistryService.setJobId(received.getJobID());
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                Message ms = getMessage(BiecoMessageTypes.RUNNING);
                sendMessage.sendMessage(ms);
                System.out.println("DEMO: RUNNING MESSAGE SENT");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                // send some values here

                String responseBodyString = null;
                try {
                    responseBodyString = sendRequestToSecurityScorer();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Gson gson = new Gson();
                Map<String, Map<String, Double>> returnedMap = gson.fromJson(responseBodyString, Map.class);
                Collection<Double> scoresList = returnedMap.get("scores").values();
                System.out.println("DEMO: RESULTS FROM SCORER:" + scoresList);
                //finishing...
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                ms = getMessage(BiecoMessageTypes.FINISHING);
                ms.setBodyFormat("JSON");
//                ms.setBody("{\"radarValues\":\"" + appRegistryService.getParam("values") + "\"}");
                ms.setBody("{\"radarValues\":\"" + scoresList + "\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                System.out.println("DEMO: FINISHING MESSAGE SENT");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                //send finish message
                ms = getMessage(BiecoMessageTypes.FINISHED);
                sendMessage.sendMessage(ms);
                System.out.println("DEMO: FINISHED MESSAGE SENT");

            }
        }.start();
    }

    private String getMetadataBlob() {
        return "Y29tcG9uZW50czoKICBjb21wb25lbnRfMToKICAgIHNlbnNpdGl2aXR5OiA1LjAKICBjb21wb25lbnRfMjoKICAgIHNlbnNpdGl2aXR5OiA3LjAKCiMgVE9ETzogdGVzdHMgdG9vbCBtYXBwaW5nPwoKdnVsbmVyYWJpbGl0aWVzOgogIHZ1bG5faW50ZWdyaXR5OgogICAgIyBUT0RPOiBjb25zaWRlciBzb21lIGZvcm1hdCB0byB0aGVzZSByZWZzPwogICAgcmVmZXJlbmNlOiBvZGRvbmUKICAgIGltcGFjdDogMy4wCiAgICB0ZXN0czoKICAgICAgLSBGaW5kT3duZXJzVGVzdAoKIyBOT1RFOiByZWZlcmVuY2UgaXMgdG8gRDcuMQpjbGFpbXM6CiAgIyBwdXJlIGNsYWltCiAgY2xhaW1fYzQ5OgogICAgY29tcG9uZW50OiBjb21wb25lbnRfMQogICAgc2VjdXJpdHlfcHJvcGVydGllczoKICAgICAgLSBhdXRoZW50aWNhdGlvbgogICAgcmVmZXJlbmNlOiBDNDkKICAgIGltcGFjdDogMS4wCiAgICB0ZXN0czogCiAgICAgIC0gTmV3T3duZXJUZXN0CiAgIyB2dWxuZXJhYmlsaXR5IGNsYWltCiAgY2xhaW1fdnVsbl9hdXRoOgogICAgY29tcG9uZW50OiBjb21wb25lbnRfMgogICAgc2VjdXJpdHlfcHJvcGVydGllczoKICAgICAgLSBpbnRlZ3JpdHkKICAgIHZ1bG5lcmFiaWxpdGllczoKICAgICAgLSB2dWxuX2ludGVncml0eQo=";
    }

    private String getGraphwalkerOutputBlob() {
        return "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8dGVzdHN1aXRlcyB0aW1lPSIyOC43NTUiIHRlc3RzPSI1IiBmYWlsdXJlcz0iMyIgZXJyb3JzPSIxIj4KICAgIDx0ZXN0c3VpdGUgbmFtZT0iR3JhcGhXYWxrZXIiIHRlc3RzPSI1IiBmYWlsdXJlcz0iMyIgZXJyb3JzPSIxIiB0aW1lPSIyOC43NTUiIHRpbWVzdGFtcD0iMjAyMS0wOC0zMVQyMzowOTowOSI+CiAgICAgICAgPHByb3BlcnRpZXM+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJhd3QudG9vbGtpdCIgdmFsdWU9InN1bi5sd2F3dC5tYWNvc3guTFdDVG9vbGtpdCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iY2xhc3N3b3JsZHMuY29uZiIgdmFsdWU9Ii91c3IvbG9jYWwvQ2VsbGFyL21hdmVuLzMuOC4yL2xpYmV4ZWMvYmluL20yLmNvbmYiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5DT0xPUkZHQkciIHZhbHVlPSIxNTswIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuQ09MT1JURVJNIiB2YWx1ZT0idHJ1ZWNvbG9yIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuQ09NTUFORF9NT0RFIiB2YWx1ZT0idW5peDIwMDMiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5ESVNQTEFZIiB2YWx1ZT0iL3ByaXZhdGUvdG1wL2NvbS5hcHBsZS5sYXVuY2hkLlFlUlpSeVhFNGcvb3JnLm1hY29zZm9yZ2UueHF1YXJ0ejowIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuSE9NRSIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuSVRFUk1fUFJPRklMRSIgdmFsdWU9IkRlZmF1bHQiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5JVEVSTV9TRVNTSU9OX0lEIiB2YWx1ZT0idzB0M3AwOjYwOTNERTczLUMwQjYtNDNBNC05MUFELTIwOEZBQjQzMDc4MCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LkpBVkFfSE9NRSIgdmFsdWU9Ii9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LkpBVkFfTUFJTl9DTEFTU184NTkxOCIgdmFsdWU9Im9yZy5jb2RlaGF1cy5wbGV4dXMuY2xhc3N3b3JsZHMubGF1bmNoZXIuTGF1bmNoZXIiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5MQU5HIiB2YWx1ZT0icGxfUEwuVVRGLTgiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5MQ19URVJNSU5BTCIgdmFsdWU9ImlUZXJtMiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LkxDX1RFUk1JTkFMX1ZFUlNJT04iIHZhbHVlPSIzLjQuNiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LkxFU1MiIHZhbHVlPSItUiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LkxPR05BTUUiIHZhbHVlPSJtYXJjaW5ieXJhIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTFNDT0xPUlMiIHZhbHVlPSJHeGZ4Y3hkeGJ4ZWdlZGFiYWdhY2FkIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTUFOUEFUSCIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhLy5udm0vdmVyc2lvbnMvbm9kZS92MTQuOS4wL3NoYXJlL21hbjovdXNyL2xvY2FsL3NoYXJlL21hbjovdXNyL3NoYXJlL21hbjovb3B0L1gxMS9zaGFyZS9tYW46L0xpYnJhcnkvQXBwbGUvdXNyL3NoYXJlL21hbjovTGlicmFyeS9EZXZlbG9wZXIvQ29tbWFuZExpbmVUb29scy9TREtzL01hY09TWC5zZGsvdXNyL3NoYXJlL21hbjovTGlicmFyeS9EZXZlbG9wZXIvQ29tbWFuZExpbmVUb29scy91c3Ivc2hhcmUvbWFuIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTUFWRU5fQ01EX0xJTkVfQVJHUyIgdmFsdWU9IiBncmFwaHdhbGtlcjp0ZXN0Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTUFWRU5fUFJPSkVDVEJBU0VESVIiIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS9ncmFwaHdhbGtlci10dXRvcmlhbC9ncmFwaHdhbGtlci1leGFtcGxlL2phdmEtcGV0Y2xpbmljIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTlZNX0JJTiIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhLy5udm0vdmVyc2lvbnMvbm9kZS92MTQuOS4wL2JpbiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52Lk5WTV9DRF9GTEFHUyIgdmFsdWU9Ii1xIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTlZNX0RJUiIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhLy5udm0iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5OVk1fSU9KU19PUkdfTUlSUk9SIiB2YWx1ZT0iaHR0cHM6Ly9pb2pzLm9yZy9kaXN0Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuTlZNX05PREVKU19PUkdfTUlSUk9SIiB2YWx1ZT0iaHR0cHM6Ly9ub2RlanMub3JnL2Rpc3QiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5PTERQV0QiIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS9ncmFwaHdhbGtlci10dXRvcmlhbC9ncmFwaHdhbGtlci1leGFtcGxlL2phdmEtcGV0Y2xpbmljIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuUEFHRVIiIHZhbHVlPSJsZXNzIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuUEFUSCIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhLy5udm0vdmVyc2lvbnMvbm9kZS92MTQuOS4wL2JpbjovVXNlcnMvbWFyY2luYnlyYS9nb29nbGUtY2xvdWQtc2RrL2JpbjovdXNyL2xvY2FsL2JpbjovdXNyL2JpbjovYmluOi91c3Ivc2Jpbjovc2Jpbjovb3B0L1gxMS9iaW4iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5QV0QiIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS9ncmFwaHdhbGtlci10dXRvcmlhbC9ncmFwaHdhbGtlci1leGFtcGxlL2phdmEtcGV0Y2xpbmljIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuU0hFTEwiIHZhbHVlPSIvYmluL3pzaCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LlNITFZMIiB2YWx1ZT0iMSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LlNTSF9BVVRIX1NPQ0siIHZhbHVlPSIvcHJpdmF0ZS90bXAvY29tLmFwcGxlLmxhdW5jaGQueERoY3hlaXU4MC9MaXN0ZW5lcnMiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5URVJNIiB2YWx1ZT0ieHRlcm0tMjU2Y29sb3IiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5URVJNX1BST0dSQU0iIHZhbHVlPSJpVGVybS5hcHAiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5URVJNX1BST0dSQU1fVkVSU0lPTiIgdmFsdWU9IjMuNC42Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuVEVSTV9TRVNTSU9OX0lEIiB2YWx1ZT0idzB0M3AwOjYwOTNERTczLUMwQjYtNDNBNC05MUFELTIwOEZBQjQzMDc4MCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LlRNUERJUiIgdmFsdWU9Ii92YXIvZm9sZGVycy9feS90YjNzNDJiajJ6bmI2bHhseDRneHo2c2gwMDAwZ24vVC8iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5VU0VSIiB2YWx1ZT0ibWFyY2luYnlyYSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZW52LlZJUlRVQUxfRU5WX0RJU0FCTEVfUFJPTVBUIiB2YWx1ZT0iIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuWFBDX0ZMQUdTIiB2YWx1ZT0iMHgwIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuWFBDX1NFUlZJQ0VfTkFNRSIgdmFsdWU9IjAiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5aU0giIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS8ub2gtbXktenNoIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJlbnYuX19DRkJ1bmRsZUlkZW50aWZpZXIiIHZhbHVlPSJjb20uZ29vZ2xlY29kZS5pdGVybTIiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImVudi5fX0NGX1VTRVJfVEVYVF9FTkNPRElORyIgdmFsdWU9IjB4MUY1OjB4MUQ6MHgyQSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZmlsZS5lbmNvZGluZyIgdmFsdWU9IlVURi04Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJmaWxlLmVuY29kaW5nLnBrZyIgdmFsdWU9InN1bi5pbyIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iZmlsZS5zZXBhcmF0b3IiIHZhbHVlPSIvIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJnb3BoZXJQcm94eVNldCIgdmFsdWU9ImZhbHNlIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLmF3dC5ncmFwaGljc2VudiIgdmFsdWU9InN1bi5hd3QuQ0dyYXBoaWNzRW52aXJvbm1lbnQiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEuYXd0LnByaW50ZXJqb2IiIHZhbHVlPSJzdW4ubHdhd3QubWFjb3N4LkNQcmludGVySm9iIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLmNsYXNzLnBhdGgiIHZhbHVlPSIvdXNyL2xvY2FsL0NlbGxhci9tYXZlbi8zLjguMi9saWJleGVjL2Jvb3QvcGxleHVzLWNsYXNzd29ybGRzLTIuNi4wLmphciIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5jbGFzcy52ZXJzaW9uIiB2YWx1ZT0iNTIuMCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5lbmRvcnNlZC5kaXJzIiB2YWx1ZT0iL0xpYnJhcnkvSmF2YS9KYXZhVmlydHVhbE1hY2hpbmVzL2Fkb3B0b3Blbmpkay04Lmpkay9Db250ZW50cy9Ib21lL2pyZS9saWIvZW5kb3JzZWQiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEuZXh0LmRpcnMiIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS9MaWJyYXJ5L0phdmEvRXh0ZW5zaW9uczovTGlicmFyeS9KYXZhL0phdmFWaXJ0dWFsTWFjaGluZXMvYWRvcHRvcGVuamRrLTguamRrL0NvbnRlbnRzL0hvbWUvanJlL2xpYi9leHQ6L0xpYnJhcnkvSmF2YS9FeHRlbnNpb25zOi9OZXR3b3JrL0xpYnJhcnkvSmF2YS9FeHRlbnNpb25zOi9TeXN0ZW0vTGlicmFyeS9KYXZhL0V4dGVuc2lvbnM6L3Vzci9saWIvamF2YSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5ob21lIiB2YWx1ZT0iL0xpYnJhcnkvSmF2YS9KYXZhVmlydHVhbE1hY2hpbmVzL2Fkb3B0b3Blbmpkay04Lmpkay9Db250ZW50cy9Ib21lL2pyZSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5pby50bXBkaXIiIHZhbHVlPSIvdmFyL2ZvbGRlcnMvX3kvdGIzczQyYmoyem5iNmx4bHg0Z3h6NnNoMDAwMGduL1QvIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLmxpYnJhcnkucGF0aCIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhL0xpYnJhcnkvSmF2YS9FeHRlbnNpb25zOi9MaWJyYXJ5L0phdmEvRXh0ZW5zaW9uczovTmV0d29yay9MaWJyYXJ5L0phdmEvRXh0ZW5zaW9uczovU3lzdGVtL0xpYnJhcnkvSmF2YS9FeHRlbnNpb25zOi91c3IvbGliL2phdmE6LiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5ydW50aW1lLm5hbWUiIHZhbHVlPSJPcGVuSkRLIFJ1bnRpbWUgRW52aXJvbm1lbnQiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEucnVudGltZS52ZXJzaW9uIiB2YWx1ZT0iMS44LjBfMjkyLWIxMCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS5zcGVjaWZpY2F0aW9uLm5hbWUiIHZhbHVlPSJKYXZhIFBsYXRmb3JtIEFQSSBTcGVjaWZpY2F0aW9uIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLnNwZWNpZmljYXRpb24udmVuZG9yIiB2YWx1ZT0iT3JhY2xlIENvcnBvcmF0aW9uIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLnNwZWNpZmljYXRpb24udmVyc2lvbiIgdmFsdWU9IjEuOCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS52ZW5kb3IiIHZhbHVlPSJBZG9wdE9wZW5KREsiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEudmVuZG9yLnVybCIgdmFsdWU9Imh0dHBzOi8vYWRvcHRvcGVuamRrLm5ldC8iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEudmVuZG9yLnVybC5idWciIHZhbHVlPSJodHRwczovL2dpdGh1Yi5jb20vQWRvcHRPcGVuSkRLL29wZW5qZGstc3VwcG9ydC9pc3N1ZXMiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEudmVyc2lvbiIgdmFsdWU9IjEuOC4wXzI5MiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS52bS5pbmZvIiB2YWx1ZT0ibWl4ZWQgbW9kZSIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS52bS5uYW1lIiB2YWx1ZT0iT3BlbkpESyA2NC1CaXQgU2VydmVyIFZNIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLnZtLnNwZWNpZmljYXRpb24ubmFtZSIgdmFsdWU9IkphdmEgVmlydHVhbCBNYWNoaW5lIFNwZWNpZmljYXRpb24iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEudm0uc3BlY2lmaWNhdGlvbi52ZW5kb3IiIHZhbHVlPSJPcmFjbGUgQ29ycG9yYXRpb24iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9ImphdmEudm0uc3BlY2lmaWNhdGlvbi52ZXJzaW9uIiB2YWx1ZT0iMS44Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJqYXZhLnZtLnZlbmRvciIgdmFsdWU9IkFkb3B0T3BlbkpESyIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0iamF2YS52bS52ZXJzaW9uIiB2YWx1ZT0iMjUuMjkyLWIxMCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ibGluZS5zZXBhcmF0b3IiIHZhbHVlPSImIzEwOyIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ibWF2ZW4uYnVpbGQudmVyc2lvbiIgdmFsdWU9IkFwYWNoZSBNYXZlbiAzLjguMiAoZWE5OGUwNWEwNDQ4MDEzMTM3MGFhMGMxMTBiOGM1NGNmNzI2YzA2ZikiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9Im1hdmVuLmNvbmYiIHZhbHVlPSIvdXNyL2xvY2FsL0NlbGxhci9tYXZlbi8zLjguMi9saWJleGVjL2NvbmYiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9Im1hdmVuLmhvbWUiIHZhbHVlPSIvdXNyL2xvY2FsL0NlbGxhci9tYXZlbi8zLjguMi9saWJleGVjIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJtYXZlbi5tdWx0aU1vZHVsZVByb2plY3REaXJlY3RvcnkiIHZhbHVlPSIvVXNlcnMvbWFyY2luYnlyYS9ncmFwaHdhbGtlci10dXRvcmlhbC9ncmFwaHdhbGtlci1leGFtcGxlL2phdmEtcGV0Y2xpbmljIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJtYXZlbi52ZXJzaW9uIiB2YWx1ZT0iMy44LjIiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9Im9zLmFyY2giIHZhbHVlPSJ4ODZfNjQiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9Im9zLm5hbWUiIHZhbHVlPSJNYWMgT1MgWCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ib3MudmVyc2lvbiIgdmFsdWU9IjEwLjE2Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJwYXRoLnNlcGFyYXRvciIgdmFsdWU9IjoiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InN1bi5hcmNoLmRhdGEubW9kZWwiIHZhbHVlPSI2NCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ic3VuLmJvb3QuY2xhc3MucGF0aCIgdmFsdWU9Ii9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZS9qcmUvbGliL3Jlc291cmNlcy5qYXI6L0xpYnJhcnkvSmF2YS9KYXZhVmlydHVhbE1hY2hpbmVzL2Fkb3B0b3Blbmpkay04Lmpkay9Db250ZW50cy9Ib21lL2pyZS9saWIvcnQuamFyOi9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZS9qcmUvbGliL3N1bnJzYXNpZ24uamFyOi9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZS9qcmUvbGliL2pzc2UuamFyOi9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZS9qcmUvbGliL2pjZS5qYXI6L0xpYnJhcnkvSmF2YS9KYXZhVmlydHVhbE1hY2hpbmVzL2Fkb3B0b3Blbmpkay04Lmpkay9Db250ZW50cy9Ib21lL2pyZS9saWIvY2hhcnNldHMuamFyOi9MaWJyYXJ5L0phdmEvSmF2YVZpcnR1YWxNYWNoaW5lcy9hZG9wdG9wZW5qZGstOC5qZGsvQ29udGVudHMvSG9tZS9qcmUvbGliL2pmci5qYXI6L0xpYnJhcnkvSmF2YS9KYXZhVmlydHVhbE1hY2hpbmVzL2Fkb3B0b3Blbmpkay04Lmpkay9Db250ZW50cy9Ib21lL2pyZS9jbGFzc2VzIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJzdW4uYm9vdC5saWJyYXJ5LnBhdGgiIHZhbHVlPSIvTGlicmFyeS9KYXZhL0phdmFWaXJ0dWFsTWFjaGluZXMvYWRvcHRvcGVuamRrLTguamRrL0NvbnRlbnRzL0hvbWUvanJlL2xpYiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ic3VuLmNwdS5lbmRpYW4iIHZhbHVlPSJsaXR0bGUiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InN1bi5jcHUuaXNhbGlzdCIgdmFsdWU9IiIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ic3VuLmlvLnVuaWNvZGUuZW5jb2RpbmciIHZhbHVlPSJVbmljb2RlQmlnIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJzdW4uamF2YS5jb21tYW5kIiB2YWx1ZT0ib3JnLmNvZGVoYXVzLnBsZXh1cy5jbGFzc3dvcmxkcy5sYXVuY2hlci5MYXVuY2hlciBncmFwaHdhbGtlcjp0ZXN0Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJzdW4uamF2YS5sYXVuY2hlciIgdmFsdWU9IlNVTl9TVEFOREFSRCIvPgogICAgICAgICAgICA8cHJvcGVydHkgbmFtZT0ic3VuLmpudS5lbmNvZGluZyIgdmFsdWU9IlVURi04Ii8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJzdW4ubWFuYWdlbWVudC5jb21waWxlciIgdmFsdWU9IkhvdFNwb3QgNjQtQml0IFRpZXJlZCBDb21waWxlcnMiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InN1bi5vcy5wYXRjaC5sZXZlbCIgdmFsdWU9InVua25vd24iLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InVzZXIuY291bnRyeSIgdmFsdWU9IlBMIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJ1c2VyLmRpciIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhL2dyYXBod2Fsa2VyLXR1dG9yaWFsL2dyYXBod2Fsa2VyLWV4YW1wbGUvamF2YS1wZXRjbGluaWMiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InVzZXIuaG9tZSIgdmFsdWU9Ii9Vc2Vycy9tYXJjaW5ieXJhIi8+CiAgICAgICAgICAgIDxwcm9wZXJ0eSBuYW1lPSJ1c2VyLmxhbmd1YWdlIiB2YWx1ZT0icGwiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InVzZXIubmFtZSIgdmFsdWU9Im1hcmNpbmJ5cmEiLz4KICAgICAgICAgICAgPHByb3BlcnR5IG5hbWU9InVzZXIudGltZXpvbmUiIHZhbHVlPSIiLz4KICAgICAgICA8L3Byb3BlcnRpZXM+CiAgICAgICAgPHRlc3RjYXNlIG5hbWU9IlBldENsaW5pY1Rlc3QiIHRpbWU9IjUuNzUxIiBjbGFzc25hbWU9ImNvbS5jb21wYW55Lm1vZGVsaW1wbGVtZW50YXRpb25zLlBldENsaW5pY1Rlc3QiPgogICAgICAgICAgICA8ZmFpbHVyZSB0eXBlPSJOb3QgZnVsZmlsbGVkIiBtZXNzYWdlPSI1NyIvPgogICAgICAgIDwvdGVzdGNhc2U+CiAgICAgICAgPHRlc3RjYXNlIG5hbWU9Ik93bmVySW5mb3JtYXRpb25UZXN0IiB0aW1lPSI1Ljc1MSIgY2xhc3NuYW1lPSJjb20uY29tcGFueS5tb2RlbGltcGxlbWVudGF0aW9ucy5Pd25lckluZm9ybWF0aW9uVGVzdCI+CiAgICAgICAgICAgIDxmYWlsdXJlIHR5cGU9Ik5vdCBmdWxmaWxsZWQiIG1lc3NhZ2U9IjAiLz4KICAgICAgICA8L3Rlc3RjYXNlPgogICAgICAgIDx0ZXN0Y2FzZSBuYW1lPSJGaW5kT3duZXJzVGVzdCIgdGltZT0iNS43NTEiIGNsYXNzbmFtZT0iY29tLmNvbXBhbnkubW9kZWxpbXBsZW1lbnRhdGlvbnMuRmluZE93bmVyc1Rlc3QiPgogICAgICAgICAgICA8ZmFpbHVyZSB0eXBlPSJOb3QgZnVsZmlsbGVkIiBtZXNzYWdlPSIyNSIvPgogICAgICAgIDwvdGVzdGNhc2U+CiAgICAgICAgPHRlc3RjYXNlIG5hbWU9Ik5ld093bmVyVGVzdCIgdGltZT0iNS43NTEiIGNsYXNzbmFtZT0iY29tLmNvbXBhbnkubW9kZWxpbXBsZW1lbnRhdGlvbnMuTmV3T3duZXJUZXN0Ij4KICAgICAgICAgICAgPGVycm9yIHR5cGU9ImphdmEubGFuZy5yZWZsZWN0Lkludm9jYXRpb25UYXJnZXRFeGNlcHRpb24iPgogICAgICAgICAgICAgICAgamF2YS5sYW5nLnJlZmxlY3QuSW52b2NhdGlvblRhcmdldEV4Y2VwdGlvbgogICAgICAgICAgICAgICAgCWF0IHN1bi5yZWZsZWN0Lk5hdGl2ZU1ldGhvZEFjY2Vzc29ySW1wbC5pbnZva2UwKE5hdGl2ZSBNZXRob2QpCiAgICAgICAgICAgICAgICAJYXQgc3VuLnJlZmxlY3QuTmF0aXZlTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShOYXRpdmVNZXRob2RBY2Nlc3NvckltcGwuamF2YTo2MikKICAgICAgICAgICAgICAgIAlhdCBzdW4ucmVmbGVjdC5EZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShEZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmphdmE6NDMpCiAgICAgICAgICAgICAgICAJYXQgamF2YS5sYW5nLnJlZmxlY3QuTWV0aG9kLmludm9rZShNZXRob2QuamF2YTo0OTgpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmdyYXBod2Fsa2VyLmNvcmUubWFjaGluZS5FeGVjdXRpb25Db250ZXh0LmV4ZWN1dGUoRXhlY3V0aW9uQ29udGV4dC5qYXZhOjI1NykKICAgICAgICAgICAgICAgIAlhdCBvcmcuZ3JhcGh3YWxrZXIuY29yZS5tYWNoaW5lLlNpbXBsZU1hY2hpbmUuZXhlY3V0ZShTaW1wbGVNYWNoaW5lLmphdmE6MjgyKQogICAgICAgICAgICAgICAgCWF0IG9yZy5ncmFwaHdhbGtlci5jb3JlLm1hY2hpbmUuU2ltcGxlTWFjaGluZS5nZXROZXh0U3RlcChTaW1wbGVNYWNoaW5lLmphdmE6MTA1KQogICAgICAgICAgICAgICAgCWF0IG9yZy5ncmFwaHdhbGtlci5qYXZhLnRlc3QuVGVzdEV4ZWN1dG9yLmV4ZWN1dGUoVGVzdEV4ZWN1dG9yLmphdmE6MjQ5KQogICAgICAgICAgICAgICAgCWF0IHN1bi5yZWZsZWN0Lk5hdGl2ZU1ldGhvZEFjY2Vzc29ySW1wbC5pbnZva2UwKE5hdGl2ZSBNZXRob2QpCiAgICAgICAgICAgICAgICAJYXQgc3VuLnJlZmxlY3QuTmF0aXZlTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShOYXRpdmVNZXRob2RBY2Nlc3NvckltcGwuamF2YTo2MikKICAgICAgICAgICAgICAgIAlhdCBzdW4ucmVmbGVjdC5EZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShEZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmphdmE6NDMpCiAgICAgICAgICAgICAgICAJYXQgamF2YS5sYW5nLnJlZmxlY3QuTWV0aG9kLmludm9rZShNZXRob2QuamF2YTo0OTgpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmdyYXBod2Fsa2VyLmphdmEudGVzdC5SZWZsZWN0aW9ucy5pbnZva2UoUmVmbGVjdGlvbnMuamF2YTo3MykKICAgICAgICAgICAgICAgIAlhdCBvcmcuZ3JhcGh3YWxrZXIuamF2YS50ZXN0LlJlZmxlY3Rvci5leGVjdXRlKFJlZmxlY3Rvci5qYXZhOjEyMykKICAgICAgICAgICAgICAgIAlhdCBvcmcuZ3JhcGh3YWxrZXIubWF2ZW4ucGx1Z2luLlRlc3RNb2pvLmV4ZWN1dGUoVGVzdE1vam8uamF2YToxNjEpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmFwYWNoZS5tYXZlbi5wbHVnaW4uRGVmYXVsdEJ1aWxkUGx1Z2luTWFuYWdlci5leGVjdXRlTW9qbyhEZWZhdWx0QnVpbGRQbHVnaW5NYW5hZ2VyLmphdmE6MTM3KQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4ubGlmZWN5Y2xlLmludGVybmFsLk1vam9FeGVjdXRvci5leGVjdXRlKE1vam9FeGVjdXRvci5qYXZhOjIxMCkKICAgICAgICAgICAgICAgIAlhdCBvcmcuYXBhY2hlLm1hdmVuLmxpZmVjeWNsZS5pbnRlcm5hbC5Nb2pvRXhlY3V0b3IuZXhlY3V0ZShNb2pvRXhlY3V0b3IuamF2YToxNTYpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmFwYWNoZS5tYXZlbi5saWZlY3ljbGUuaW50ZXJuYWwuTW9qb0V4ZWN1dG9yLmV4ZWN1dGUoTW9qb0V4ZWN1dG9yLmphdmE6MTQ4KQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4ubGlmZWN5Y2xlLmludGVybmFsLkxpZmVjeWNsZU1vZHVsZUJ1aWxkZXIuYnVpbGRQcm9qZWN0KExpZmVjeWNsZU1vZHVsZUJ1aWxkZXIuamF2YToxMTcpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmFwYWNoZS5tYXZlbi5saWZlY3ljbGUuaW50ZXJuYWwuTGlmZWN5Y2xlTW9kdWxlQnVpbGRlci5idWlsZFByb2plY3QoTGlmZWN5Y2xlTW9kdWxlQnVpbGRlci5qYXZhOjgxKQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4ubGlmZWN5Y2xlLmludGVybmFsLmJ1aWxkZXIuc2luZ2xldGhyZWFkZWQuU2luZ2xlVGhyZWFkZWRCdWlsZGVyLmJ1aWxkKFNpbmdsZVRocmVhZGVkQnVpbGRlci5qYXZhOjU2KQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4ubGlmZWN5Y2xlLmludGVybmFsLkxpZmVjeWNsZVN0YXJ0ZXIuZXhlY3V0ZShMaWZlY3ljbGVTdGFydGVyLmphdmE6MTI4KQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4uRGVmYXVsdE1hdmVuLmRvRXhlY3V0ZShEZWZhdWx0TWF2ZW4uamF2YTozMDUpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmFwYWNoZS5tYXZlbi5EZWZhdWx0TWF2ZW4uZG9FeGVjdXRlKERlZmF1bHRNYXZlbi5qYXZhOjE5MikKICAgICAgICAgICAgICAgIAlhdCBvcmcuYXBhY2hlLm1hdmVuLkRlZmF1bHRNYXZlbi5leGVjdXRlKERlZmF1bHRNYXZlbi5qYXZhOjEwNSkKICAgICAgICAgICAgICAgIAlhdCBvcmcuYXBhY2hlLm1hdmVuLmNsaS5NYXZlbkNsaS5leGVjdXRlKE1hdmVuQ2xpLmphdmE6OTcyKQogICAgICAgICAgICAgICAgCWF0IG9yZy5hcGFjaGUubWF2ZW4uY2xpLk1hdmVuQ2xpLmRvTWFpbihNYXZlbkNsaS5qYXZhOjI5MykKICAgICAgICAgICAgICAgIAlhdCBvcmcuYXBhY2hlLm1hdmVuLmNsaS5NYXZlbkNsaS5tYWluKE1hdmVuQ2xpLmphdmE6MTk2KQogICAgICAgICAgICAgICAgCWF0IHN1bi5yZWZsZWN0Lk5hdGl2ZU1ldGhvZEFjY2Vzc29ySW1wbC5pbnZva2UwKE5hdGl2ZSBNZXRob2QpCiAgICAgICAgICAgICAgICAJYXQgc3VuLnJlZmxlY3QuTmF0aXZlTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShOYXRpdmVNZXRob2RBY2Nlc3NvckltcGwuamF2YTo2MikKICAgICAgICAgICAgICAgIAlhdCBzdW4ucmVmbGVjdC5EZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmludm9rZShEZWxlZ2F0aW5nTWV0aG9kQWNjZXNzb3JJbXBsLmphdmE6NDMpCiAgICAgICAgICAgICAgICAJYXQgamF2YS5sYW5nLnJlZmxlY3QuTWV0aG9kLmludm9rZShNZXRob2QuamF2YTo0OTgpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmNvZGVoYXVzLnBsZXh1cy5jbGFzc3dvcmxkcy5sYXVuY2hlci5MYXVuY2hlci5sYXVuY2hFbmhhbmNlZChMYXVuY2hlci5qYXZhOjI4MikKICAgICAgICAgICAgICAgIAlhdCBvcmcuY29kZWhhdXMucGxleHVzLmNsYXNzd29ybGRzLmxhdW5jaGVyLkxhdW5jaGVyLmxhdW5jaChMYXVuY2hlci5qYXZhOjIyNSkKICAgICAgICAgICAgICAgIAlhdCBvcmcuY29kZWhhdXMucGxleHVzLmNsYXNzd29ybGRzLmxhdW5jaGVyLkxhdW5jaGVyLm1haW5XaXRoRXhpdENvZGUoTGF1bmNoZXIuamF2YTo0MDYpCiAgICAgICAgICAgICAgICAJYXQgb3JnLmNvZGVoYXVzLnBsZXh1cy5jbGFzc3dvcmxkcy5sYXVuY2hlci5MYXVuY2hlci5tYWluKExhdW5jaGVyLmphdmE6MzQ3KQogICAgICAgICAgICAgICAgQ2F1c2VkIGJ5OiBFbGVtZW50IHNob3VsZCBoYXZlIHRleHQgJ251bWVyaWMgdmFsdWUgb3V0IG9mIGJvdW5kcyAoJmx0OzEwIGRpZ2l0cyZndDsuJmx0OzAgZGlnaXRzJmd0OyBleHBlY3RlZCknIHsuaGVscC1pbmxpbmV9CiAgICAgICAgICAgICAgICBFbGVtZW50OiAnJmx0O3NwYW4gY2xhc3M9ImhlbHAtaW5saW5lIiZndDt3YXJ0b8WbxIcgbGljemJvd2Egc3BvemEgemFrcmVzdSAob2N6ZWtpd2FubyAmbHQ7bGljemJhIGN5ZnI6IDEwJmd0OywmbHQ7bGljemJhIGN5ZnI6IDAmZ3Q7KSZsdDsvc3BhbiZndDsnCiAgICAgICAgICAgICAgICBTY3JlZW5zaG90OiBmaWxlOi9Vc2Vycy9tYXJjaW5ieXJhL2dyYXBod2Fsa2VyLXR1dG9yaWFsL2dyYXBod2Fsa2VyLWV4YW1wbGUvamF2YS1wZXRjbGluaWMvYnVpbGQvcmVwb3J0cy90ZXN0cy8xNjMwNDQ0MTY0MDk1LjAucG5nCiAgICAgICAgICAgICAgICBQYWdlIHNvdXJjZTogZmlsZTovVXNlcnMvbWFyY2luYnlyYS9ncmFwaHdhbGtlci10dXRvcmlhbC9ncmFwaHdhbGtlci1leGFtcGxlL2phdmEtcGV0Y2xpbmljL2J1aWxkL3JlcG9ydHMvdGVzdHMvMTYzMDQ0NDE2NDA5NS4wLmh0bWwKICAgICAgICAgICAgICAgIFRpbWVvdXQ6IDQgcy4KICAgICAgICAgICAgICAgIAlhdCBjb20uY29kZWJvcm5lLnNlbGVuaWRlLmltcGwuV2ViRWxlbWVudFNvdXJjZS5jaGVja0NvbmRpdGlvbihXZWJFbGVtZW50U291cmNlLmphdmE6MTE1KQogICAgICAgICAgICAgICAgCWF0IGNvbS5jb2RlYm9ybmUuc2VsZW5pZGUuY29tbWFuZHMuU2hvdWxkLmV4ZWN1dGUoU2hvdWxkLmphdmE6MzApCiAgICAgICAgICAgICAgICAJYXQgY29tLmNvZGVib3JuZS5zZWxlbmlkZS5jb21tYW5kcy5TaG91bGQuZXhlY3V0ZShTaG91bGQuamF2YToxNCkKICAgICAgICAgICAgICAgIAlhdCBjb20uY29kZWJvcm5lLnNlbGVuaWRlLmNvbW1hbmRzLkNvbW1hbmRzLmV4ZWN1dGUoQ29tbWFuZHMuamF2YToxNTQpCiAgICAgICAgICAgICAgICAJYXQgY29tLmNvZGVib3JuZS5zZWxlbmlkZS5pbXBsLlNlbGVuaWRlRWxlbWVudFByb3h5LmRpc3BhdGNoQW5kUmV0cnkoU2VsZW5pZGVFbGVtZW50UHJveHkuamF2YToxMjgpCiAgICAgICAgICAgICAgICAJYXQgY29tLmNvZGVib3JuZS5zZWxlbmlkZS5pbXBsLlNlbGVuaWRlRWxlbWVudFByb3h5Lmludm9rZShTZWxlbmlkZUVsZW1lbnRQcm94eS5qYXZhOjgwKQogICAgICAgICAgICAgICAgCWF0IGNvbS5zdW4ucHJveHkuJFByb3h5NDEuc2hvdWxkSGF2ZShVbmtub3duIFNvdXJjZSkKICAgICAgICAgICAgICAgIAlhdCBjb20uY29tcGFueS5tb2RlbGltcGxlbWVudGF0aW9ucy5OZXdPd25lclRlc3Qudl9JbmNvcnJlY3REYXRhKE5ld093bmVyVGVzdC5qYXZhOjQzKQogICAgICAgICAgICAgICAgCS4uLiAzNyBtb3JlCjwvZXJyb3I+CiAgICAgICAgPC90ZXN0Y2FzZT4KICAgICAgICA8dGVzdGNhc2UgbmFtZT0iVmV0ZXJpbmFyaWFuc1Rlc3QiIHRpbWU9IjUuNzUxIiBjbGFzc25hbWU9ImNvbS5jb21wYW55Lm1vZGVsaW1wbGVtZW50YXRpb25zLlZldGVyaW5hcmlhbnNUZXN0Ii8+CiAgICA8L3Rlc3RzdWl0ZT4KPC90ZXN0c3VpdGVzPgo=";
    }

    private void event() {
        System.out.println("Got event " + received.getBody());
    }

    private void data() {
        System.out.println("Got data " + received.getBody());
    }

    private String sendRequestToSecurityScorer() throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        String valuesString = appRegistryService.getParam("values");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, valuesString);
        Request request = new Request.Builder()
                .url(appRegistryService.getSecurityScorerUrl())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = client.newCall(request).execute();
        String responseString = Objects.requireNonNull(response.body()).string();
        return responseString;
    }

    private void configure() {
        this.appRegistryService.setState(BiecoMessageTypes.CONFIGURING);
        this.appRegistryService.setJobId(received.getJobID());
        System.out.println("Got config data " + received.getBody() + " " + this.appRegistryService.getJobId());
        System.out.println("DEMO: RECEIVED CONFIGURE MESSAGE");

        Gson gson = new Gson();
        Map<String, String> bodyData = gson.fromJson(received.getBody(), Map.class);

        appRegistryService.addParam("values", bodyData.get("values"));
        System.out.println("DEMO: SAVED CONFIGURE PAYLOAD IN APP REGISTRY SERVICE");

        new Thread() {
            @Override
            public void run() {
                Message ms = getMessage(BiecoMessageTypes.READY);
                System.out.println("Sending Ready Message");
                sendMessage.sendMessage(ms);
                appRegistryService.setState(BiecoMessageTypes.READY);
            }
        }.start();
    }

    private void heartbeat() {
        this.appRegistryService.setToolId(received.getDestinationID());
    }

    private void sendStatus() {
        new Thread() {
            @Override
            public void run() {
                Message message = getMessage(appRegistryService.getState());
                sendMessage.sendMessage(message);
            }
        }.start();
    }

    private void validateMessage(Message message) {
//        if (!message.validateCRC()) {
//            System.out.println("Got Invalid message: "+ message);
//            System.out.println(message.getCrc()+" old");
//            message.calculateCRC();
//            System.out.println(message.getCrc()+" new");
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CRC\n");
//        }
        System.out.println("DEMO (ONLY): SKIPPING CRC VALIDATION...");
    }

    public Message getMessage(String type) {
        System.out.println("New message T:" + appRegistryService.getToolId() + " J:" + appRegistryService.getJobId());
        Message message = new Message();
        message.setMessageType(type);
        message.setBody("");
        message.setJobID(appRegistryService.getJobId());
        message.setSourceID(appRegistryService.getToolId());
        message.setEvent(type);
        message.setBodyFormat("JSON");
        message.setTimestamp(new Timestamp(System.currentTimeMillis()).toString());
        message.calculateCRC();
        return message;
    }
}
