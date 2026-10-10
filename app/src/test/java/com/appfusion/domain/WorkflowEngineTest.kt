package com.appfusion.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowEngineTest {
    private class FakeOperations : WorkflowOperations {
        val calls = mutableListOf<String>()
        var failSummary = false
        override suspend fun extractPdf(uri: String): String { calls += "extract"; return "PDF text." }
        override fun summarize(text: String): String { calls += "summary:$text"; if (failSummary) error("Provider unavailable") ; return "Short." }
    }

    @Test fun validatesWorkflowCreationAndTypes() {
        val workflow = WorkflowDefinition("1", "PDF summary", listOf(WorkflowStep(Operation.EXTRACT_PDF_TEXT), WorkflowStep(Operation.SUMMARIZE_TEXT)))
        WorkflowValidator.validate(workflow, ValueType.PDF_URI)
        val invalid = WorkflowDefinition("2", "Bad order", listOf(WorkflowStep(Operation.EXTRACT_PDF_TEXT)))
        assertTrue(runCatching { WorkflowValidator.validate(invalid, ValueType.TEXT) }.exceptionOrNull() is WorkflowValidationException)
    }

    @Test fun executesStepsInOrderAndPassesOutput() = runBlocking {
        val ops = FakeOperations()
        val flow = WorkflowDefinition("1", "PDF flow", listOf(WorkflowStep(Operation.EXTRACT_PDF_TEXT), WorkflowStep(Operation.CLEAN_TEXT), WorkflowStep(Operation.SUMMARIZE_TEXT)))
        val run = WorkflowEngine(ops).execute(flow, WorkflowInput.PdfUri("content://source"))
        assertEquals(RunStatus.SUCCEEDED, run.status)
        assertEquals(listOf("extract", "summary:PDF text."), ops.calls)
        assertEquals("Short.", run.results.last().value)
    }

    @Test fun reportsFailureWithoutReportingSuccess() = runBlocking {
        val ops = FakeOperations().apply { failSummary = true }
        val flow = WorkflowDefinition("1", "Text flow", listOf(WorkflowStep(Operation.ACCEPT_TEXT), WorkflowStep(Operation.SUMMARIZE_TEXT)))
        val run = WorkflowEngine(ops).execute(flow, WorkflowInput.Text("Hello."))
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals("Provider unavailable", run.error)
        assertEquals(1, run.results.size)
    }

    @Test fun malformedOrEmptyWorkflowIsRejected() {
        assertTrue(runCatching { WorkflowValidator.validate(WorkflowDefinition("x", "", emptyList()), ValueType.TEXT) }.isFailure)
        assertTrue(runCatching { WorkflowValidator.validate(WorkflowDefinition("x", "Unknown", emptyList()), ValueType.TEXT) }.exceptionOrNull() is WorkflowValidationException)
    }
}
