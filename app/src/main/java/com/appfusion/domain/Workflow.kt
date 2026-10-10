package com.appfusion.domain

enum class ValueType { TEXT, PDF_URI }
enum class Operation { ACCEPT_TEXT, EXTRACT_PDF_TEXT, CLEAN_TEXT, SUMMARIZE_TEXT, DISPLAY_RESULT }
enum class RunStatus { SUCCEEDED, FAILED, CANCELLED }

data class WorkflowStep(val operation: Operation)
data class WorkflowDefinition(val id: String, val name: String, val steps: List<WorkflowStep>)
data class StepResult(val operation: Operation, val value: Any)
data class WorkflowRun(val status: RunStatus, val results: List<StepResult>, val error: String? = null)
sealed interface WorkflowInput { data class Text(val value: String) : WorkflowInput; data class PdfUri(val value: String) : WorkflowInput }

interface WorkflowOperations {
    suspend fun extractPdf(uri: String): String
    fun summarize(text: String): String
}

class WorkflowValidationException(message: String) : IllegalArgumentException(message)

object WorkflowValidator {
    private val transitions = mapOf(
        Operation.ACCEPT_TEXT to (ValueType.TEXT to ValueType.TEXT),
        Operation.EXTRACT_PDF_TEXT to (ValueType.PDF_URI to ValueType.TEXT),
        Operation.CLEAN_TEXT to (ValueType.TEXT to ValueType.TEXT),
        Operation.SUMMARIZE_TEXT to (ValueType.TEXT to ValueType.TEXT),
        Operation.DISPLAY_RESULT to (ValueType.TEXT to ValueType.TEXT),
    )
    fun validate(workflow: WorkflowDefinition, initial: ValueType) {
        if (workflow.name.isBlank()) throw WorkflowValidationException("Give this workflow a name.")
        if (workflow.steps.isEmpty()) throw WorkflowValidationException("Add at least one step.")
        var current = initial
        workflow.steps.forEachIndexed { index, step ->
            val signature = transitions[step.operation] ?: throw WorkflowValidationException("Unsupported operation at step ${index + 1}.")
            if (current != signature.first) throw WorkflowValidationException("Step ${index + 1} needs ${signature.first}, but receives $current.")
            current = signature.second
        }
    }
}

class WorkflowEngine(private val operations: WorkflowOperations) {
    suspend fun execute(workflow: WorkflowDefinition, input: WorkflowInput): WorkflowRun {
        val initial = when (input) { is WorkflowInput.Text -> ValueType.TEXT; is WorkflowInput.PdfUri -> ValueType.PDF_URI }
        val results = mutableListOf<StepResult>()
        return try {
            WorkflowValidator.validate(workflow, initial)
            var value: Any = when (input) { is WorkflowInput.Text -> input.value; is WorkflowInput.PdfUri -> input.value }
            for (step in workflow.steps) {
                value = when (step.operation) {
                    Operation.ACCEPT_TEXT -> requireText(value)
                    Operation.EXTRACT_PDF_TEXT -> operations.extractPdf(value as String)
                    Operation.CLEAN_TEXT -> requireText(value).trim().replace(Regex("[\\t ]+"), " ")
                    Operation.SUMMARIZE_TEXT -> operations.summarize(requireText(value))
                    Operation.DISPLAY_RESULT -> requireText(value)
                }
                results += StepResult(step.operation, value)
            }
            WorkflowRun(RunStatus.SUCCEEDED, results)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            WorkflowRun(RunStatus.CANCELLED, results.toList(), "Workflow cancelled.")
        } catch (error: Exception) {
            WorkflowRun(RunStatus.FAILED, results.toList(), error.message ?: "The workflow could not be completed.")
        }
    }
    private fun requireText(value: Any): String = value as? String
        ?: throw WorkflowValidationException("This step received data in an unsupported format.")
}
