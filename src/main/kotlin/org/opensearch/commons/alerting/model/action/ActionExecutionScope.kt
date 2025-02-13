package org.opensearch.commons.alerting.model.action

import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.common.xcontent.XContentParserUtils
import org.opensearch.commons.notifications.model.BaseModel
import org.opensearch.core.xcontent.ToXContent
import org.opensearch.core.xcontent.XContentBuilder
import org.opensearch.core.xcontent.XContentParser
import java.io.IOException

sealed class ActionExecutionScope : BaseModel {

    enum class Type { PER_ALERT, PER_EXECUTION }

    companion object {
        const val PER_ALERT_FIELD = "per_alert"
        const val PER_EXECUTION_FIELD = "per_execution"
        const val ACTIONABLE_ALERTS_FIELD = "actionable_alerts"

        @JvmStatic
        @Throws(IOException::class)
        fun parse(xcp: XContentParser): ActionExecutionScope {
            var type: Type? = null
            var actionExecutionScope: ActionExecutionScope? = null
            val alertFilter = mutableSetOf<AlertCategory>()

            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, xcp.currentToken(), xcp)
            while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                val fieldName = xcp.currentName()
                xcp.nextToken()

                // If the type field has already been set, the user has provided more than one type of schedule
                if (type != null) {
                    throw IllegalArgumentException("You can only specify one type of action execution scope.")
                }

                when (fieldName) {
                    PER_ALERT_FIELD -> {
                        type = Type.PER_ALERT
                        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                            val perAlertFieldName = xcp.currentName()
                            xcp.nextToken()
                            when (perAlertFieldName) {
                                ACTIONABLE_ALERTS_FIELD -> {
                                    XContentParserUtils.ensureExpectedToken(
                                        XContentParser.Token.START_ARRAY,
                                        xcp.currentToken(),
                                        xcp
                                    )
                                    val allowedCategories = AlertCategory.values().map { it.toString() }
                                    while (xcp.nextToken() != XContentParser.Token.END_ARRAY) {
                                        val alertCategory = xcp.text()
                                        if (!allowedCategories.contains(alertCategory)) {
                                            throw IllegalStateException("Actionable alerts should be one of $allowedCategories")
                                        }
                                        alertFilter.add(AlertCategory.valueOf(alertCategory))
                                    }
                                }
                                else -> throw IllegalArgumentException(
                                    "Invalid field [$perAlertFieldName] found in per alert action execution scope."
                                )
                            }
                        }
                    }
                    PER_EXECUTION_FIELD -> {
                        type = Type.PER_EXECUTION
                        while (xcp.nextToken() != XContentParser.Token.END_OBJECT) {
                            // empty while block
                        }
                    }
                    else -> throw IllegalArgumentException("Invalid field [$fieldName] found in action execution scope.")
                }
            }

            if (type == Type.PER_ALERT) {
                actionExecutionScope = PerAlertActionScope(alertFilter)
            } else if (type == Type.PER_EXECUTION) {
                actionExecutionScope = PerExecutionActionScope()
            }

            return requireNotNull(actionExecutionScope) { "Action execution scope is null." }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): ActionExecutionScope {
            val type = sin.readEnum(ActionExecutionScope.Type::class.java)
            return if (type == Type.PER_ALERT) {
                PerAlertActionScope(sin)
            } else {
                PerExecutionActionScope(sin)
            }
        }
    }

    abstract fun getExecutionScope(): Type
}

data class PerAlertActionScope(
    val actionableAlerts: Set<AlertCategory>
) : ActionExecutionScope() {

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this(
        sin.readSet { si -> si.readEnum(AlertCategory::class.java) } // alertFilter
    )

    override fun getExecutionScope(): Type = Type.PER_ALERT

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(PER_ALERT_FIELD)
            .field(ACTIONABLE_ALERTS_FIELD, actionableAlerts.toTypedArray())
            .endObject()
        return builder.endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        out.writeCollection(actionableAlerts) { o, v -> o.writeEnum(v) }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): PerAlertActionScope {
            return PerAlertActionScope(sin)
        }
    }
}

class PerExecutionActionScope() : ActionExecutionScope() {

    @Throws(IOException::class)
    constructor(sin: StreamInput) : this()

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    // Creating an equals method that just checks class type rather than reference since this is currently stateless.
    // Otherwise, it would have been a dataclass which would have handled this.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        return true
    }

    override fun getExecutionScope(): Type = Type.PER_EXECUTION

    override fun toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        builder.startObject()
            .startObject(PER_EXECUTION_FIELD)
            .endObject()
        return builder.endObject()
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        // body empty
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): PerExecutionActionScope {
            return PerExecutionActionScope(sin)
        }
    }
}

enum class AlertCategory { DEDUPED, NEW, COMPLETED }
