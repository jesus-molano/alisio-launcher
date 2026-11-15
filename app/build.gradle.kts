import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.jesusmolano.alisio"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.jesusmolano.alisio"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            // Uso personal por adb: release optimizada pero firmada con la clave de debug.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}

/** Falla si el manifiesto final (ya fusionado con el de las dependencias) pide INTERNET. Ver docs/PLAN.md §7. */
abstract class CheckNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val file = mergedManifest.get().asFile
        val forbidden = Regex("""android:name\s*=\s*"android\.permission\.INTERNET"""")
        if (forbidden.containsMatchIn(file.readText())) {
            throw GradleException(
                "El manifiesto final pide android.permission.INTERNET y Alisio no lo permite: $file"
            )
        }
    }
}

val checkNoInternetPermission =
    tasks.register("checkNoInternetPermission") {
        group = "verification"
        description = "Falla si el manifiesto final de alguna variante pide INTERNET."
    }

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val variantCheck =
            tasks.register<CheckNoInternetPermissionTask>("checkNoInternetPermission$name") {
                group = "verification"
                description = "Falla si el manifiesto final de ${variant.name} pide INTERNET."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            }
        checkNoInternetPermission.configure { dependsOn(variantCheck) }
        // Ningún APK se ensambla ni se instala sin pasar la comprobación.
        tasks.matching { it.name == "assemble$name" || it.name == "install$name" }.configureEach {
            dependsOn(variantCheck)
        }
    }
}

tasks.named("check") { dependsOn(checkNoInternetPermission) }
