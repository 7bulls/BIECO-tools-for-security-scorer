package com.example.mocktool.controllers;

import com.example.mocktool.services.AppRegistryService;
import com.example.mocktool.services.OrchestratorSendMessage;
import com.google.gson.Gson;
import lombok.NoArgsConstructor;
import org.bieco.common.enums.BiecoMessageTypes;
import org.bieco.common.enums.BiecoToolStatuses;
import org.bieco.common.models.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Map;

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
    public String processMessage(@RequestBody Message message, @RequestHeader("Authorization") String token) {
        if(!appRegistryService.getToken().equals(token)) {
            System.out.println("Invalid token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access Forbidden\n");
        }
        validateMessage(message);
        if(!message.getMessageType().equals(BiecoMessageTypes.HEARTBEAT)) {
            System.out.println("Got message. S:"+appRegistryService.getState()+
                    " T:"+appRegistryService.getToolId()+
                    " J:"+appRegistryService.getJobId());
            System.out.println(message);
        }
        parseMessage(message);
        return null;
    }

    private void parseMessage(Message message) {
        this.received = message;
        switch(message.getMessageType()) {
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
        new Thread(){
            @Override
            public void run() {
                Message ms;
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                //send a finishing message with some data
                ms = getMessage(BiecoMessageTypes.HALTING);
                ms.setBody("{\"someKey\":\"someValue\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                //send finish message
                ms = getMessage(BiecoMessageTypes.HALTED);
                sendMessage.sendMessage(ms);
            }
        }.start();
    }

    private void stop() {
        System.out.println("Got Stop message, stopping...");
        new Thread(){
            @Override
            public void run() {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                //send a finishing message with some data
                Message ms = getMessage(BiecoMessageTypes.FINISHING);
                ms.setBody("{\"someKey\":\"someValue\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                //send finish message
                ms = getMessage(BiecoMessageTypes.FINISHED);
                sendMessage.sendMessage(ms);
            }
        }.start();
    }

    private void start() {
        System.out.println("Got Start message, running");
        appRegistryService.setState(BiecoToolStatuses.RUNNING);
        appRegistryService.setJobId(received.getJobID());
        new Thread() {
            @Override
            public void run() {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                Message ms = getMessage(BiecoMessageTypes.RUNNING);
                sendMessage.sendMessage(ms);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                // send some values here
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                ms = getMessage(BiecoMessageTypes.ERROR);
                ms.setBodyFormat("text");
                ms.setBody("Failure on line 55 of file main.cpp");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                ms = getMessage(BiecoMessageTypes.ERROR);
                ms.setBodyFormat("text");
                ms.setBody("Possible Zero-Day Exploit in module org.example.serialinput.ReadInput");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                //finishing...
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                ms = getMessage(BiecoMessageTypes.FINISHING);
                ms.setBodyFormat("JSON");
                ms.setBody("{\"radarValues\":\""+appRegistryService.getParam("values")+"\"}");
                ms.calculateCRC();
                sendMessage.sendMessage(ms);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                //send finish message
                ms = getMessage(BiecoMessageTypes.FINISHED);
                sendMessage.sendMessage(ms);
            }
        }.start();
    }

    private void event() {
        System.out.println("Got event "+received.getBody());
    }

    private void data() {
        System.out.println("Got data "+received.getBody());
    }

    private void configure() {
        this.appRegistryService.setState(BiecoMessageTypes.CONFIGURING);
        this.appRegistryService.setJobId(received.getJobID());
        System.out.println("Got config data "+received.getBody()+" "+this.appRegistryService.getJobId());
        Gson gson = new Gson();
        Map<String, String> bodyData = gson.fromJson(received.getBody(), Map.class);
        appRegistryService.addParam("values", bodyData.get("values"));
        new Thread(){
            @Override
            public void run() {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
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
        new Thread(){
            @Override
            public void run() {
                Message message = getMessage(appRegistryService.getState());
                sendMessage.sendMessage(message);
            }
        }.start();
    }

    private void validateMessage(Message message) {
        if (!message.validateCRC()) {
            System.out.println("Got Invalid message: "+ message);
            System.out.println(message.getCrc()+" old");
            message.calculateCRC();
            System.out.println(message.getCrc()+" new");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CRC\n");
        }
    }

    public Message getMessage(String type) {
        System.out.println("New message T:"+appRegistryService.getToolId()+" J:"+appRegistryService.getJobId());
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
