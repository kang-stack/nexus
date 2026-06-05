package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.model.ReplySegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Kang
 * @since 2026-06-01
 */
class ResponseAssemblerTest {

    private ResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ResponseAssembler();
    }

    @Test
    void assembleDiagnosisShouldReturnDiagnosisSegment() {
        ReplySegment seg = assembler.assembleDiagnosis("信号弱", 1, 3);

        assertEquals(ReplySegment.DIAGNOSIS, seg.getMessageType());
        assertEquals("信号弱", seg.getContent());
        assertEquals(1, seg.getSequenceNumber());
        assertEquals(3, seg.getTotalSequence());
        assertNull(seg.getButton());
        assertNull(seg.getLinks());
    }

    @Test
    void assembleDiagnosisShouldSkipEmptyContent() {
        ReplySegment seg = assembler.assembleDiagnosis("", 1, 1);
        assertNull(seg);
    }

    @Test
    void assembleDiagnosisShouldSkipNullContent() {
        ReplySegment seg = assembler.assembleDiagnosis(null, 1, 1);
        assertNull(seg);
    }

    @Test
    void assembleDiagnosisShouldSkipBlankContent() {
        ReplySegment seg = assembler.assembleDiagnosis("   ", 1, 1);
        assertNull(seg);
    }

    @Test
    void assembleHumanServiceShouldReturnHumanServiceSegment() {
        ReplySegment seg = assembler.assembleHumanService("正在为您转接人工客服", 1, 1);

        assertEquals(ReplySegment.HUMAN_SERVICE, seg.getMessageType());
        assertEquals("正在为您转接人工客服", seg.getContent());
        assertEquals(1, seg.getSequenceNumber());
        assertEquals(1, seg.getTotalSequence());
    }

    @Test
    void templateFillingStillWorks() {
        ReplySegment seg = assembler.assemble("Hello ${name}", "TEXT", null, null, null, true, 1, 2,
                Map.of("name", "World"));

        assertEquals("TEXT", seg.getMessageType());
        assertEquals("Hello World", seg.getContent());
        assertTrue(seg.isEnding());
        assertEquals(1, seg.getSequenceNumber());
        assertEquals(2, seg.getTotalSequence());
    }
}
