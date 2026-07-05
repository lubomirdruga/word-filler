package com.lubomirdruga.wordfiller.provider

import com.lubomirdruga.wordfiller.WordFillerConfig
import com.lubomirdruga.wordfiller.WordFillerException
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.exception.VelocityException
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.apache.velocity.util.introspection.SecureUberspector
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * Provider for evaluating Velocity templates with data context.
 *
 * Nested templates resolve to `/<templateBasePath>/<template>/<name>.vm` under the
 * root defined by the shared [WordFillerConfig].
 *
 * By default the engine runs with Velocity's [SecureUberspector], which blocks
 * reflection escapes from templates (e.g. `$model.class.classLoader`) while normal
 * property and method access (`$model.name.toUpperCase()`) keeps working. Pass
 * `secureIntrospection = false` only if your templates are fully trusted and need
 * unrestricted introspection.
 *
 * @param config Configuration shared with [com.lubomirdruga.wordfiller.WordFiller]; pass the same instance to both
 * @param secureIntrospection Whether to restrict template introspection (default: true)
 */
class VelocityDataProvider(
    override val config: WordFillerConfig,
    private val secureIntrospection: Boolean = true,
) : TemplateDataProvider {
    private val velocityEngine: VelocityEngine = VelocityEngine()

    init {
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath")
        velocityEngine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader::class.java.name)
        if (secureIntrospection) {
            velocityEngine.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, SecureUberspector::class.java.name)
        }

        velocityEngine.init()
    }

    /**
     * Evaluate a Velocity expression or template reference.
     *
     * @param expression The expression to evaluate, or "|templateName|" for a template reference
     * @param template The parent template name for locating nested templates
     * @param value The data object to be available as 'model' in the template
     * @return The evaluated result as a string
     * @throws WordFillerException if a nested template is missing or evaluation fails
     */
    override fun evaluateExpression(
        expression: String,
        template: String,
        value: Any?,
    ): String {
        val context = VelocityContext()
        context.put("model", value)

        StringWriter().use { writer ->
            if (expression.startsWith("|")) {
                // expression contains template name
                val templateName = expression.substring(1, expression.length - 1).trim()
                val resourcePath = velocityTemplatePath(template, templateName)
                logger.debug("Resolving nested template '{}' at {}", templateName, resourcePath)
                val resource =
                    javaClass.getResourceAsStream(resourcePath)
                        ?: throw WordFillerException(
                            "Nested template '$templateName' not found at $resourcePath (referenced from template '$template')",
                        )
                InputStreamReader(resource, StandardCharsets.UTF_8).use { reader ->
                    try {
                        velocityEngine.evaluate(context, writer, templateName, reader)
                    } catch (e: VelocityException) {
                        throw WordFillerException(
                            "Failed to evaluate nested template '$templateName' of template '$template'",
                            e,
                        )
                    }
                }
            } else {
                // expression is a velocity expression
                try {
                    velocityEngine.evaluate(context, writer, template, expression)
                } catch (e: VelocityException) {
                    throw WordFillerException(
                        "Failed to evaluate expression '$expression' in template '$template'",
                        e,
                    )
                }
            }
            return writer.toString()
        }
    }

    /**
     * Classpath location of a nested Velocity template belonging to a Word template.
     */
    private fun velocityTemplatePath(
        template: String,
        subTemplate: String,
    ): String = config.resolve("$template/$subTemplate.vm")

    companion object {
        private val logger = LoggerFactory.getLogger(VelocityDataProvider::class.java)
    }
}
