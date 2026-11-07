package com.enverolab.comandas.ui

import androidx.compose.runtime.saveable.Saver
import com.enverolab.comandas.domain.*
import org.json.JSONArray
import org.json.JSONObject

val LinesSaver =
    Saver<List<Line>, String>(
        save = { lines ->
            JSONArray()
                .apply {
                    lines.forEach { l ->
                        put(
                            JSONObject()
                                .put("id", l.id)
                                .put("product", l.productId)
                                .put("variant", l.variantId ?: JSONObject.NULL)
                                .put("code", l.code)
                                .put("name", l.name)
                                .put("description", l.description)
                                .put("quantity", l.quantity.toPlainString())
                                .put("price", l.price.toPlainString())
                                .put("percent", l.discountPercent.toPlainString())
                                .put("discount", l.discountAmount.toPlainString())
                        )
                    }
                }
                .toString()
        },
        restore = { text ->
            val a = JSONArray(text)
            List(a.length()) { i ->
                val l = a.getJSONObject(i)
                Line(
                    l.getLong("id"),
                    l.getLong("product"),
                    if (l.isNull("variant")) null else l.getLong("variant"),
                    l.getString("code"),
                    l.getString("name"),
                    l.getString("description"),
                    amount(l.getString("quantity")),
                    amount(l.getString("price")),
                    amount(l.getString("percent")),
                    amount(l.getString("discount")),
                )
            }
        },
    )
val PaymentsSaver =
    Saver<List<Payment>, String>(
        save = { payments ->
            JSONArray()
                .apply {
                    payments.forEach { p ->
                        put(
                            JSONObject()
                                .put("method", p.methodId)
                                .put("name", p.methodName)
                                .put("received", p.received.toPlainString())
                                .put("factor", p.factor.toPlainString())
                                .put("tip", p.tip.toPlainString())
                                .put("currency", p.currency)
                                .put("person", p.person)
                                .put(
                                    "assigned",
                                    JSONObject(
                                        p.assignments
                                            .mapKeys { it.key.toString() }
                                            .mapValues { it.value.toPlainString() }
                                    ),
                                )
                        )
                    }
                }
                .toString()
        },
        restore = { text ->
            val a = JSONArray(text)
            List(a.length()) { i ->
                val p = a.getJSONObject(i)
                val assignments = p.getJSONObject("assigned")
                Payment(
                    p.getLong("method"),
                    p.getString("name"),
                    amount(p.getString("received")),
                    amount(p.getString("factor")),
                    amount(p.getString("tip")),
                    p.getString("currency"),
                    p.getString("person"),
                    assignments.keys().asSequence().associate {
                        it.toLong() to amount(assignments.getString(it))
                    },
                )
            }
        },
    )

val NullableRowSaver =
    Saver<Row?, String>(
        save = { row -> if (row == null) "" else JSONObject(row).toString() },
        restore = { text ->
            if (text.isEmpty()) null
            else
                JSONObject(text).let { obj ->
                    obj.keys().asSequence().associateWith { key ->
                        if (obj.isNull(key)) null else obj.get(key)
                    }
                }
        },
    )
val StringMapSaver =
    Saver<Map<String, String>, String>(
        save = { JSONObject(it).toString() },
        restore = {
            JSONObject(it).let { obj -> obj.keys().asSequence().associateWith(obj::getString) }
        },
    )
val QuantityMapSaver =
    Saver<Map<Long, String>, String>(
        save = { JSONObject(it.mapKeys { entry -> entry.key.toString() }).toString() },
        restore = {
            JSONObject(it).let { obj ->
                obj.keys().asSequence().associate { key -> key.toLong() to obj.getString(key) }
            }
        },
    )
