package io.github.chsbuffer.revancedxposed.spotify.misc

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.RequireAppVersion
import io.github.chsbuffer.revancedxposed.SkipTest
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType

val productStateProtoFingerprint = fingerprint {
    returns("Ljava/util/Map;")
    classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
}

val attributesMapField =
    findFieldDirect { productStateProtoFingerprint().usingFields.single().field }

val buildQueryParametersFingerprint = findMethodDirect {
    findMethod {
        matcher {
            strings("trackRows", "device_type:tablet")
        }
    }.single()
}
val contextFromJsonFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            opcodes(
                Opcode.INVOKE_STATIC,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_STATIC
            )
            methodMatcher {
                name("fromJson")
                declaredClass(
                    "voiceassistants.playermodels.ContextJsonAdapter", StringMatchType.EndsWith
                )
            }
        }
    }.getOrElse {
        // 9.2+: Moshi adapter replaced by a kotlinx.serialization serializer.
        fingerprint {
            returns("Lcom/spotify/player/model/Context;")
            methodMatcher {
                name("deserialize")
                declaredClass(
                    "voiceassistants.playermodels.ContextSerializer", StringMatchType.EndsWith
                )
            }
        }
    }
}

val contextMenuViewModelClass = findClassDirect {
    return@findClassDirect runCatching {
        fingerprint {
            strings("ContextMenuViewModel(header=")
        }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}

// 9.2 dropped getViewModel: menu items are now plain data classes, upsell removal needs a new approach.
@get:RequireAppVersion(maxVersion = "9.2.0.0")
val viewModelClazz = findClassDirect {
    findMethod {
        findFirst = true
        matcher { name("getViewModel") }
    }.single().returnType!!
}

@get:RequireAppVersion(maxVersion = "9.2.0.0")
val isPremiumUpsellField = findFieldDirect {
    viewModelClazz().fields.filter { it.typeName == "boolean" }[1]
}

@SkipTest
fun structureGetSectionsFingerprint(className: String) = fingerprint {
    classMatcher { className(className, StringMatchType.EndsWith) }
    methodMatcher {
        addUsingField {
            usingType = UsingType.Read
            name = "sections_"
        }
    }
}

@get:RequireAppVersion(maxVersion = "9.2.0.0")
val homeStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("homeapi.proto.HomeStructure")

// 9.2+: slot-based home maps each casita Section on its own; a null result drops the section.
// Hook the inner mapper: the outer wrapper (matched by its error string) is small enough to get inlined.
@get:RequireAppVersion(minVersion = "9.2.0.0")
val homeSectionMapperFingerprint = findMethodDirect {
    val wrapper = fingerprint {
        parameters("Lcom/spotify/casita/v1/resolved/Section;")
        strings(" section to HomeFeatureType")
    }
    wrapper.invokes.single {
        it.declaredClassName == wrapper.declaredClassName && it.paramTypeNames == wrapper.paramTypeNames
    }
}
val browseStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("browsita.v1.resolved.BrowseStructure")

val pendragonJsonFetchMessageRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}

val pendragonJsonFetchMessageListRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageListRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}
