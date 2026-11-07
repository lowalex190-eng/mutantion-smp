package com.mutationsmp;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/** One attribute change that a mutation applies to its owner. */
public record Mod(Attribute attribute, double amount, AttributeModifier.Operation operation) {
}
