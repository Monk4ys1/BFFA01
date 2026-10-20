package me.clip.placeholderapi.expansion;

import me.clip.placeholderapi.PlaceholderHook;

import java.util.List;

/**
 * Compile-time stub matching the methods this plugin overrides.
 * It is not shipped. See the shade filter.
 */
public abstract class PlaceholderExpansion extends PlaceholderHook {

    public abstract String getIdentifier();

    public abstract String getAuthor();

    public abstract String getVersion();

    public String getName() {
        return getIdentifier();
    }

    public boolean persist() {
        return false;
    }

    public boolean register() {
        return false;
    }

    public List<String> getPlaceholders() {
        return List.of();
    }
}
