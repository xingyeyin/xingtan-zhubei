package com.xingtan.application.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingtan.kb.entity.KbChunk;
import com.xingtan.kb.entity.KbDocument;
import com.xingtan.kb.mapper.KbChunkMapper;
import com.xingtan.kb.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 教材课文库初始化：从 classpath:db/textbooks.json 导入小学/初中全学段课文与单元目录
 * （课标、教材、模板、案例等其余知识库内容由建表脚本提供）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookDataInitializer implements ApplicationRunner {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        try (InputStream in = new ClassPathResource("db/textbooks.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            int docs = 0;
            int chunks = 0;
            int skipped = 0;
            for (JsonNode node : root) {
                String title = node.path("title").asText();
                Long exists = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getDocType, "TEXTBOOK")
                        .eq(KbDocument::getTitle, title));
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }
                KbDocument doc = new KbDocument();
                doc.setTitle(title);
                doc.setDocType("TEXTBOOK");
                doc.setSubject(node.path("subject").asText());
                doc.setGrade(node.path("grade").asText());
                doc.setTextbookVersion(node.path("version").asText());
                doc.setStatus(1);
                documentMapper.insert(doc);
                docs++;

                int seq = 0;
                for (JsonNode unit : node.path("units")) {
                    StringBuilder content = new StringBuilder();
                    content.append(doc.getTitle()).append(" ");
                    content.append(unit.path("name").asText()).append("：");
                    StringBuilder items = new StringBuilder();
                    for (JsonNode item : unit.path("items")) {
                        items.append(item.asText()).append("、");
                    }
                    if (items.length() > 0) {
                        items.setLength(items.length() - 1);
                    }
                    content.append(items);
                    KbChunk chunk = new KbChunk();
                    chunk.setDocId(doc.getId());
                    chunk.setSeq(seq++);
                    chunk.setContent(content.toString());
                    chunkMapper.insert(chunk);
                    chunks++;
                }
            }
            log.info("教材课文库初始化完成：新增 {} 册 / {} 个单元条目，跳过已存在 {} 册", docs, chunks, skipped);
        } catch (Exception e) {
            log.error("教材课文库初始化失败", e);
        }
    }
}
