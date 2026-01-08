package io.github.starfabricated.nanoforge.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameData {
    private static final HashMap<String,String> data =new HashMap<>();
    public static Map<String,String> getData(){
        return Collections.unmodifiableMap(data);
    }

    public static void setData(String k,String v){
        data.put(k,v);
    }

}
