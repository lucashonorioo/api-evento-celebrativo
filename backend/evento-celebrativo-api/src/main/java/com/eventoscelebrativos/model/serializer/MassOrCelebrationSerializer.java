package com.eventoscelebrativos.model.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class MassOrCelebrationSerializer extends JsonSerializer<Boolean> {

    @Override
    public void serialize(Boolean value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            if (value) {
                gen.writeString("missa");
            } else {
                gen.writeString("celebração");
            }
        } else {
            gen.writeNull();
        }
    }
}