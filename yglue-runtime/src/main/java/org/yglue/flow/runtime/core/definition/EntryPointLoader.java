package org.yglue.flow.runtime.core.definition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 入口点加载器
 * 从本地文件或资源中加载入口点定义
 */
public class EntryPointLoader {

    private final ObjectMapper objectMapper;

    public EntryPointLoader() {
        this(createDefaultObjectMapper());
    }

    public EntryPointLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建默认的 ObjectMapper，配置忽略未知属性
     */
    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 忽略未知属性，避免反序列化时出错
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 加载入口点定义
     * 
     * @return 入口点定义
     * @throws IOException 如果读取文件失败
     */
    public EntryPointsDefinition load() throws IOException {
        JsonNode root = readEntryPoints();
        if (root == null || root.isMissingNode()) {
            return new EntryPointsDefinition();
        }
        return objectMapper.convertValue(root, EntryPointsDefinition.class);
    }

    private JsonNode readEntryPoints() throws IOException {
        Path local = Path.of(".ygflow", "entrypoints.json");
        if (Files.exists(local)) {
            return objectMapper.readTree(local.toFile());
        }
        InputStream is = EntryPointLoader.class.getResourceAsStream("/ygflow/entrypoints.json");
        if (is != null) {
            return objectMapper.readTree(is);
        }
        return null;
    }
}
