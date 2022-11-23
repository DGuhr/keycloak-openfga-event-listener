package io.dguhr.keycloak.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.EnvUtil;
import org.keycloak.common.util.Environment;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class FileServiceHandler extends ServiceHandler {

    private static final Logger logger = Logger.getLogger(FileServiceHandler.class);

    public FileServiceHandler(KeycloakSession session, Config.Scope config){
        super(session, config);
        validateConfig();
    }

    @Override
    public void handle(String eventId, String eventValue) throws ExecutionException, InterruptedException, TimeoutException {
        logger.debug("[OpenFgaEventListener] File handler is writing event id: " + eventId + " with value: " + eventValue + " to file: " + getFile());
        var filePath = System.getProperty("kc.home.dir");

        Path p = Paths.get(filePath+"ofga_export.txt");
        try {
            Files.write(p, List.of(eventValue + System.lineSeparator()), CREATE, APPEND);
        } catch (IOException e) {
            logger.error("Not possible! nah! Path: " + p, e);
        }
        //Future<RecordMetadata> metaData = producer.send(record);
        //RecordMetadata recordMetadata = metaData.get(30, TimeUnit.SECONDS);
        /*LOG.debug("[OpenFgaEventListener] Received new metadata. \n" +
                "Topic:" + recordMetadata.topic() + "\n" +
                "Partition: " + recordMetadata.partition() + "\n" +
                "Key:" + record.key() + "\n" +
                "Offset: " + recordMetadata.offset() + "\n" +
                "Timestamp: " + recordMetadata.timestamp());*/
    }

    private String getFile() {
        return "foo.csv";
    }

    @Override
    public void validateConfig() {

    }
}
