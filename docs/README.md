# NanoForge
<div  align="middle" >
<img src="assets/nanoforge.png" width="192">

<b>Next-Generation Modding Framework for Starsector
</div>

## Introduction


## Usage
### Framework
copy game jar to `lib/gameJar`\
copy `graphics` `data` `sounds`  dir to `assets`

run  `runVanilla` task\
or `runLanchWrapper` task

### CorePlugin
Similar to `IFMLLoadingPlugin`, you need implement `IClassTransformer` and `INanoCorePlugin` 

```java
import java.util.List;

public class MyPlugin implements INanoCorePlugin {

    @Override
    public List<String> getASMTransformerClass() {
        return List.of("myplugin.asm.myAsmTransformer");
    }
    
}
```
### how to use (mixin)
Similar to `MixinBooter`, you need implement `IMixinLoader`
```java
public class MyMixinLoader implements IMixinLoader {
    
    @Override
    public List<String> getMixinConfigs() {
        return List.of("myplugin.mixins.json");
    }
}
```
