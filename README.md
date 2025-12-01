# Nano Framework of Reverse Game Engineering
Now can launch game in IDEA

We have `Tweaker`, `Mixin`, And `EventBus`, but no `ModLoader`.\

You can make what's missing yourself :)

ps: now only support 0.98a+ (freaking Java7 :/ )
### how to use (framework)
copy game jar to `lib/gameJar`\
copy `graphics` `data` `sounds`  dir to `assets`

run  `runVanilla` task\
or `runLanchWrapper` task
### how to use (coremod)
like `IFMLLoadingPlugin`, just implement `IClassTransformer` then...
```java
public class MyPlugin implements INanoCorePlugin {
    ...
    
    @Override
    public String[] getASMTransformerClass(){
         //Return Your ClassTransformer Class Name
    }
    
    ...
}
```
### how to use (mixin)
like `MixinBooter` , implement `IMixinLoader` then...
```java
public class MyMixinLoader implements IMixinLoader {
    ...
    
    @Override
    public List<String> getMixinConfigs() {
        //Return your modid.mixin.json file name
    }
    ...
}
```

### ZH_CN
致敬传奇加载器FORGE，NanoForge现已抵达远行星号😁

现已支持1.12 `IFMLLoadingPlugin`风味`CoreMod`与`Mixinbooter`式`Mixin`加载\
真伟大啊，cpw。

不过我可能得换个许可证了，forge/fml还有mixinbooter是gpl💧