package com.lubomirdruga.wordfiller

import com.lubomirdruga.wordfiller.config.WordFillerConfig
import com.lubomirdruga.wordfiller.provider.TemplateDataProvider
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import java.io.InputStreamReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * Provider for evaluating Velocity templates with data context.
 *
 * Nested template locations are derived from [WordFillerConfig.velocityTemplatePath],
 * so the config is the single source of truth for template paths.
 *
 * @param config Configuration shared with [WordFiller]; pass the same instance to both
 */
class VelocityDataProvider(
    override val config: WordFillerConfig,
) : TemplateDataProvider {
    private val velocityEngine: VelocityEngine = VelocityEngine()

    init {
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath")
        velocityEngine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader::class.java.name)

        velocityEngine.init()
    }

    /**
     * Evaluate a Velocity expression or template reference.
     *
     * @param expression The expression to evaluate, or "|templateName|" for a template reference
     * @param template The parent template name for locating nested templates
     * @param value The data object to be available as 'model' in the template
     * @return The evaluated result as a string
     */
    override fun evaluateExpression(expression: String, template: String, value: Any?): String {
        val context = VelocityContext()
        context.put("model", value)

        StringWriter().use { writer ->
            if (expression.startsWith("|")) {
                // expression contains template name
                val templateName = expression.substring(1, expression.length - 1).trim()
                val resourcePath = config.velocityTemplatePath(template, templateName)
                val resource = javaClass.getResourceAsStream(resourcePath)
                    ?: return "$templateName: template not found at $resourcePath"
                InputStreamReader(resource, StandardCharsets.UTF_8).use { reader ->
                    velocityEngine.evaluate(context, writer, "", reader)
                }

            } else {
                // expression is a velocity expression
                velocityEngine.evaluate(context, writer, "", expression)
            }
            return writer.toString()
        }
    }
}
