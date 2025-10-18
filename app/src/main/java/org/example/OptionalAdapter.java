/* copypasted straight from Gemini */

package org.example;

import com.google.gson.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalAdapter implements JsonSerializer<Optional<?>>, JsonDeserializer<Optional<?>> {

    @Override
    public Optional<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        // Get the T in Optional<T>
        Type innerType = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];
        // Deserialize the inner T
        Object value = context.deserialize(json, innerType);
        return Optional.ofNullable(value);
    }

    @Override
    public JsonElement serialize(Optional<?> src, Type typeOfSrc, JsonSerializationContext context) {
        if (src.isPresent()) {
            // Get the T in Optional<T>
            Type innerType = ((ParameterizedType) typeOfSrc).getActualTypeArguments()[0];
            // Serialize the inner T
            return context.serialize(src.get(), innerType);
        } else {
            // Serialize as null
            return JsonNull.INSTANCE;
        }
    }
}
