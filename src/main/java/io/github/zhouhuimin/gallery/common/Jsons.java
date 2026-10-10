package io.github.zhouhuimin.gallery.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class Jsons {
    private final ObjectMapper mapper;
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    public JsonNode read(String value) { try { return mapper.readTree(value); } catch(Exception e) { throw new IllegalStateException(e); } }
}
