package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ym.ymchat.service.language.LanguageService;

class LanguageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesLocalizedValuesAndDoesNotLeakEnglishFallbackInChineseMode() throws IOException {
        writeLocales("""
            common:
              greeting: 'Ni hao'
              list:
                - 'First zh'
                - 'Second zh'
            """, """
            common:
              greeting: 'Hello'
              fallback: 'Fallback'
              list:
                - 'First'
                - 'Second'
            """);

        LanguageService languageService = new LanguageService(tempDir.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("Options.Language", "zh_cn");
        languageService.reload(config);

        assertEquals("Ni hao", languageService.get("common.greeting"));
        assertNotEquals("Fallback", languageService.get("common.fallback"));
        assertEquals("&#FF5555\u8bed\u8a00\u6587\u672c\u7f3a\u5931\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002", languageService.get("common.fallback"));
        assertEquals(List.of("First zh", "Second zh"), languageService.resolveConfiguredList(List.of("lang:common.list")));
        assertEquals("Ni hao", languageService.resolveConfigured("lang:common.greeting"));
    }

    @Test
    void nonChineseLocaleFallsBackToDefaultChineseBundle() throws IOException {
        writeLocales("""
            common:
              fallback: 'Chinese fallback'
            """, """
            common:
              greeting: 'Hello'
            """);

        LanguageService languageService = new LanguageService(tempDir.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("Options.Language", "en_us");
        languageService.reload(config);

        assertEquals("Hello", languageService.get("common.greeting"));
        assertEquals("Chinese fallback", languageService.get("common.fallback"));
    }

    private void writeLocales(String zhCn, String enUs) throws IOException {
        Path langDir = tempDir.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("zh_cn.yml"), zhCn, StandardCharsets.UTF_8);
        Files.writeString(langDir.resolve("en_us.yml"), enUs, StandardCharsets.UTF_8);
    }
}
