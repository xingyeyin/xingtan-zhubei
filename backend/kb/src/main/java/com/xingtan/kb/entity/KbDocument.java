package com.xingtan.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档（课标/教材/模板/乡土案例）
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String docType;
    private Long userId;
    private String subject;
    private String grade;
    private String textbookVersion;
    private Integer status;
    private String meta;
    private LocalDateTime createdAt;
}
