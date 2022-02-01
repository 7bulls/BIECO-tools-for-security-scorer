package org.bieco.common.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.zip.CRC32;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Message {
    @NotBlank
    private String jobID;                   // ID of the current job
    @NotBlank
    private String timestamp;               // timestamp for the event generation
    @NotBlank
    private String messageType;             // the message class

    private String sourceIP = "0.0.0.0";    // IP of the message generator
    @NotBlank
    private String sourceID;                // ID of the message generator

    private String destinationID="";        // ID of the destination, if it is known to the emitting tool

    @NotBlank
    private String event;                   // the human-readable event name

    private String accessLevel = "public";  // access level [private/protected/public] (protected -> with password)

    private Integer priority = 5;           // 1 to 5, lower is better
    @NotNull
    private Long crc;                       // calculated in order to assure message integrity

    private String bodyFormat = "text";     // [text/HTML/JSON/XML/CSV/BLOB]

    private String bodyCompression = "none";// [none/zip/tar/gz] (default is none)

    private String body = "";               // the actual body of the message

    // -----------------------------------------------------------------------------------------------------------------

    public void calculateCRC() {
        this.crc = this.calcCRC();
    }

    public boolean validateCRC() {
        return this.calcCRC().equals(this.crc);
    }

    private Long calcCRC() {
        long total = 0;
        total += this.getCrcValue(jobID.getBytes());
        total += this.getCrcValue(timestamp.getBytes());
        total += this.getCrcValue(messageType.getBytes());
        total += this.getCrcValue(sourceIP.getBytes());
        total += this.getCrcValue(sourceID.getBytes());
        total += this.getCrcValue(event.getBytes());
        total += this.getCrcValue(accessLevel.getBytes());
        total += this.getCrcValue(priority);
        total += this.getCrcValue(bodyFormat.getBytes());
        total += this.getCrcValue(bodyCompression.getBytes());
        total += this.getCrcValue(body.getBytes());
        return total;
    }

    private Long getCrcValue(byte[] val) {
        CRC32 crc = new CRC32();
        crc.update(val);
        return crc.getValue();
    }

    private Long getCrcValue(Integer val) {
        CRC32 crc = new CRC32();
        crc.update(val);
        return crc.getValue();
    }
}
