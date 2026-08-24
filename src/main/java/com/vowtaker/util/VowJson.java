package com.vowtaker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class VowJson
{
    private VowJson()
    {
    }

    public static Gson createGson()
    {
        return new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    }
}
