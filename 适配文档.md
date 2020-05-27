# Fat-AAR
多AAR方式合并


----

20200527
本版本暂只适配  build:gradle:3.4.1  gradle-5.1.1-all.zip

----

> 前人经验前排放
>
> https://github.com/adwiv/android-fat-aar  
> https://www.jianshu.com/p/f88ff677ac95?t=1490962970518  
> https://www.huahuaxie.com/fat-aar-implementation-analysis/  
> https://blog.csdn.net/coutliuxing/article/details/81672694    

------  

### 适用场景 

作为一个SDK供应商开发者,针对不同的客户,需要客制化的配置与接口,纯JAVA层还好说,C层不同功能需要加载不同的SO库与资源文件,更有甚者,还需要依赖适配硬件的JAR库或者AAR库,因此,寻求如何最小体积的将所需功能打包到一个SDK中,提出这个需求的人就该剁碎喂猪

<br>    

### 使用环境  build:gradle:3.4.1  Gradle Version: 5.1   

  如果是不同的版本,自定义下适配目录应该就可以搞定
  
<br>      

### 设计思路   

1 Gradle中app有productFlavors用来做多渠道打包,因此同样也可以借助productFlavors来实现AAR的不同打包  
2 将依赖的Model Project先打包成AAR,则可使用Model的productFlavors来实现不同SO和资源文件的加载,之后只需要考虑AAR的合并问题 

<br>

----

### 适配流程　　　

####  1 在工程类的Model中做 ProductFlavors的Configuration声明,并配置artifacts　

-- 1 未声明 Lib_Prod1Lib_DevRelease ,则在合并AAR时无法直接调用assembleLib_Prod1Lib_DevRelease编译　　   
-- 2 未配置 artifacts ,则dependencies.moduleArtifacts返回[] 空数组　



```JavaScript

---- modelproject/build.gradle----

configurations {

    Lib_Prod1Lib_DevRelease
    Lib_Prod1Lib_RelRelease
    Lib_Prod2Lib_DevRelease
    Lib_Prod2Lib_RelRelease

    artifacts.add("Lib_Prod1Lib_DevRelease", file('\\build\\outputs\\aar/modelproject-Lib_Prod1-Lib_Dev-release.aar'))
    artifacts.add("Lib_Prod1Lib_RelRelease", file('\\build\\outputs\\aar/modelproject-Lib_Prod1-Lib_Rel-release.aar'))
    artifacts.add("Lib_Prod2Lib_DevRelease", file('\\build\\outputs\\aar/modelproject-Lib_Prod2-Lib_Dev-release.aar'))
    artifacts.add("Lib_Prod2Lib_RelRelease", file('\\build\\outputs\\aar/modelproject-Lib_Prod2-Lib_Rel-release.aar'))
}

```
<br>


#### 2 在合并AAR的 Model 中进行 ProductFlavors 客制化    

-- 1 针对工程类的Model,需要根据ProductFlavors 配置 configuration    
-- 2 针对AAR类的Model,每种AAR需要配置 embeddedAAR


```JavaScript

---- modelaar/build.gradle----

android {
    
    flavorDimensions "SDK_HUAN"
    productFlavors{
    
        SDK_Product1{
            dependencies{
                embeddedProj project(path:":modelproject",configuration:"Lib_Prod1Lib_RelRelease")
            }
        }
    
        SDK_Product1NoLocal{
            dependencies{
                embeddedProj project(path:":modelproject",configuration:"Lib_Prod1Lib_RelRelease")
            }
        }
    
        SDK_Prod2Local{
            dependencies{
                embeddedProj project(path:":modelproject",configuration:"Lib_Prod2Lib_DevRelease")
            }
        }
}

dependencies {

    //对于AAR工程,只需依赖一次
    embeddedAAR1 project(path: ':modellocal-release',configuration: 'default')

}


```
<br> 

####  3 FAT-AAR中 客制化 configurations和Product依赖    

--1 此处搭配 ProductName 和configuration  来区分并寻找对应的  embeddedProj     
--2 在此做三方AAR的依赖客制化


```JavaScript

---- modelaar/fat-aar-huan.gradle----

//--适配工程 依据Product自定义

configurations {
    embeddedProj
    embeddedAar1
    embeddedAar2
}
def customDependenciesProj(ProductName,configuration) {
    log "** Proj custom -- p: "+ProductName + " c: "+configuration

    if ("SDK_Product1".contentEquals(ProductName)){
        return configuration.find("Prod1")
    }else if ("SDK_Product1NoLocal".contentEquals(ProductName)){
        return configuration.find("Prod1")
    }else if("SDK_Product2".contentEquals(ProductName)){
        return configuration.find("Prod2")
    }
    else {
        new Exception("Custom Proj Error !!")
    }
    return false
}

def customDependenciesAar(product,type){
    log "** AAR custom --p: "+product//+"  t: "+type
    def aarList = new ArrayList();
    if ( "SDK_Product1".contentEquals(product) || "SDK_Product2".contentEquals(product))
    {
        aarList.addAll(configurations.embeddedAar1.resolvedConfiguration.firstLevelModuleDependencies)
    }else {
        new Exception("Custom AAR Error !!");
    }
    return aarList
}
//--适配工程 自定义

```
<br>

---

### 主流程解析 
>文件: fat-aar-huan.gradle

####  1 适配 Product 对应的embeddedProj,并构建 assemble$ 任务

-- 测试发现,针对 embeddedProj configuration 这种方式, 工程Model不会生成AAR文件,需要配置任务依赖

-- 其实也可以不配置生成AAR,而是将 工程Model 的Build下各个临时文件的内容 插入到 AarModel 的流程中,就是有点繁琐

```JavaScript

// 获取所有工程式Model的依赖
def depsProj = new ArrayList(configurations.embeddedProj.resolvedConfiguration.firstLevelModuleDependencies)
log("depsProj size " + depsProj.size())
log("depsProj: " + depsProj)

//这是studio右上角点击的assemble任务
def taskAAR = tasks.getByName('assemble'+ productType)

def dependencies = new ArrayList()

depsProj.reverseEach {
    
    def isProductDeps = customDependenciesProj(productName,it.configuration)
    if (isProductDeps){
        // 测试发现 Gradle对于配置configurations不会自动生成AAR,(default可以)
        // 所以需要配置 assemble 依赖强制生成AAR,这导致需要两次点击才是所需版本
        def projModule = project(':'+it.moduleName)
        def projAssemble ='assemble'+it.configuration
        def taskAssemble =  projModule.tasks.getByName(projAssemble);
        log("-- depsProject  "+projModule+" >>Module "+ taskAssemble+"--AAR "+taskAAR)
        taskAAR.dependsOn taskAssemble

        //将对应AAR添加到依赖包中
        dependencies.add(it)
    }
}

```
<br>

####  2 适配 Product对应的 embeddedAAR,这个需要客制化


```JavaScript

def depsAar = customDependenciesAar(productName,typeName)

dependencies.addAll(depsAar)

```
<br>

####  3 上面已经匹配到各个Product对应依赖的AAR了,下面就是遍历每个AAR,并解压到指定目录

```JavaScript
// Jar 包集合
def embeddedJars = new ArrayList()
// AAR 解压路径
def embeddedAarDirs = new ArrayList()

dependencies.reverseEach {
    //log("dependencies 11 " +it)
    // log("dependencies 22 " +it.name)
    // log("dependencies 33 " +it.moduleName)
    log("-- dependencies Artifacts " +it.moduleArtifacts)

    //a02 遍历每一个模块
    it.moduleArtifacts.each { artifact ->
        log("-- -- AAR File: $artifact  path: $artifact.file.absolutePath")
        structureAAR(productName,artifact,embeddedAarDirs,embeddedJars);
    }
}

def  structureAAR( productName,artifact,embeddedAarDirs,embeddedJars){

    if (!file(artifact.file.absolutePath).exists()) {
        log("** structureAAR file not exists !!! ")
        return
    }

    if (artifact.type == 'aar') {
        //AAR的解压目录  gradle3.4 之后网上说的几个文件夹都改掉了
        //所以这里需要自己解压并拷贝到指定目录 fat-aar/aar_name
        def aarNamePath = "$dir_fataar/${artifact.name}"

        //标记解压路径集合
        if (!embeddedAarDirs.contains(aarNamePath)) {
            //解压并拷贝
            copy {
                from zipTree( artifact.file.absolutePath)
                into aarNamePath
            }

            embeddedAarDirs.add(aarNamePath)
        }
        log("structureAAR ziptree to: $aarNamePath")

    } else if (artifact.type == 'jar') {
        //Jar文件直接标记到Jar集合
        if (!embeddedJars.contains(artifact.file)) {
            embeddedJars.add(artifact.file)
        }
    } else {
        log("Unsupported Type: ${artifact.type}")
        throw new Exception("Unsupported Type: ${artifact.type}")
    }
}

```
<br>

####  4 需要合并的AAR已经准备好了,下面就是将各种资源插入到Gradle的原有的构建流程中


> AAR的目录结构     
AAR的本身是一个zip文件，强制包含以下文件：    
/AndroidManifest.xml    
/classes.jar    
/res/    
/R.txt    
>
> 另外，AAR文件可以包括以下可选条目中的一个或多个：   
/assets/     
/libs/name.jar       
/jni/abi_name/name.so (where abi_name is one of the Android supported ABIs) 
/proguard.txt    
/lint.jar   
>

<br>

<table>
    <tr>
        <td width = 15% align="center">资源类型</td> 
        <td width = 20% align="center">Task名称</td> 
        <td width = 20% align="center">依赖gradle</td> 
        <td width = 60% align="center">备注</td>  
    </tr>
    <tr>
        <td align="center">res</td> 
        <td>taskEmbedResources</td> 
        <td>被依赖:<br>generate${productType}Assets</td> 
        <td>首先找到Gradle的打包res的任务:generate${productType}Assets, 然后在这个任务之前将需要合并的aar的res路径插入:android.sourceSets.main.res.srcDirs</td>
    </tr>
    <tr>
        <td align="center">assets</td>
        <td>taskEmbedAssets</td>
        <td>被依赖:<br>generate${productType}Resources</td>
        <td>同上,插入assert</td>
    </tr>
     <tr>
        <td align="center">jni</td>
        <td>taskEmbedJniLibs</td>
        <td>被依赖:<br>merge${productType}JniLibFolders</td>
        <td>同上,插入jniLibs</td>
    </tr>
     <tr>
        <td align="center">proguard</td>
        <td>taskEmbedProguard</td>
        <td>被依赖:<br>taskEmbedResources</td>
        <td>遍历aar解压目录，将子模块的proguard.txt文件内容追加到主工程build目录内的proguard.txt中</td>
    </tr>
     <tr>
        <td align="center">R</td>
        <td>taskCollectRClass<br>taskEmbedRClass</td>
        <td>依赖:<br>taskGeneratedRJava<br> 被依赖:<br>taskEmbedJavaJars<br> transformClassesAndResourcesWithSyncLibJarsFor${productType}</td>
        <td>首先找到Gradle的打包res的任务:generate${productType}Assets, 然后在这个任务之前将需要合并的aar的res路径插入:android.sourceSets.main.res.srcDirs</td>
    </tr>
     <tr>
        <td align="center">jar</td>
        <td>embedJavaJars</td>
        <td>依赖:<br>embedRClass<br>  被依赖:bundleReleaseAar</td>
        <td>首先找到Gradle的打包res的任务:generate${productType}Assets, 然后在这个任务之前将需要合并的aar的res路径插入:android.sourceSets.main.res.srcDirs</td>
    </tr>

  <tr>
        <td align="center">class.jar</td>
        <td>taskEmbedClassJar</td>
        <td>依赖:<br>compile${productType}JavaWithJavac<br>被依赖:<br>transformClassesAndResourcesWithSyncLibJarsFor${productType}</td>
        <td>首先找到Gradle的打包res的任务:generate${productType}Assets, 然后在这个任务之前将需要合并的aar的res路径插入:android.sourceSets.main.res.srcDirs</td>
    </tr>
      <tr>
        <td align="center">manifest</td>
        <td>taskEmbedManifests</td>
        <td>依赖:<br>process${productType}Manifest<br>被依赖:<br>bundle${productType}Aar</td>
        <td>首先找到Gradle的打包res的任务:generate${productType}Assets, 然后在这个任务之前将需要合并的aar的res路径插入:android.sourceSets.main.res.srcDirs</td>
    </tr>

</table>


