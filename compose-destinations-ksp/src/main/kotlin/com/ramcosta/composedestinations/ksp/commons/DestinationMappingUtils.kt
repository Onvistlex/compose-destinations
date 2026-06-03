package com.ramcosta.composedestinations.ksp.commons

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.ramcosta.composedestinations.codegen.commons.CORE_PACKAGE_NAME
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_STYLE_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_WRAPPERS_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.IllegalDestinationsSetup
import com.ramcosta.composedestinations.codegen.model.DestinationStyleType
import com.ramcosta.composedestinations.codegen.model.Importable

class DestinationMappingUtils (
    private val resolver: Resolver
) {

    fun getDestinationWrappers(annotation: KSAnnotation): List<Importable>? {
        val ksTypes = annotation.findArgumentValue<ArrayList<KSType>>(DESTINATION_ANNOTATION_WRAPPERS_ARGUMENT)
            ?: return null

        return ksTypes.map {
            if ((it.declaration as? KSClassDeclaration)?.classKind != ClassKind.OBJECT) {
                throw IllegalDestinationsSetup("DestinationWrappers need to be objects! (check ${it.declaration.simpleName.asString()})")
            }

            Importable(
                it.declaration.simpleName.asString(),
                it.declaration.qualifiedName!!.asString()
            )
        }
    }

    fun getDestinationStyleType(
        annotation: KSAnnotation,
        locationError: String,
        allowNothing: Boolean = false // if true, will consider nothing as null, else it will throw exception
    ): DestinationStyleType? {
        val ksStyleType = annotation.findArgumentValue<KSType>(DESTINATION_ANNOTATION_STYLE_ARGUMENT)
            ?: return null

        if (allowNothing) {
            if ((ksStyleType.declaration as? KSClassDeclaration)?.isNothing == true) {
                return null
            }
        }

        if (defaultStyle.isAssignableFrom(ksStyleType)) {
            return DestinationStyleType.Default
        }

        if (bottomSheetStyle != null && bottomSheetStyle!!.isAssignableFrom(ksStyleType)) {
            return DestinationStyleType.BottomSheet
        }

        // Check known styles before resolving importable — in KSP 2.3+ findActualClassDeclaration()
        // may return null for nested classes obtained from annotation arguments, causing a false
        // "not resolvable" error before the type-check is ever reached.
        if (dialogStyle.isAssignableFrom(ksStyleType)) {
            // Fall back to the dialogStyle's own declaration when ksStyleType.declaration
            // is a KSTypeParameter (null qualifiedName) — common with KSP 2.3+ for KClass args
            val importable = ksStyleType.resolveImportable()
                ?: dialogStyle.resolveImportable()
                ?: throw IllegalDestinationsSetup("Parameter $DESTINATION_ANNOTATION_STYLE_ARGUMENT of Destination annotation in $locationError was not resolvable: please review it.")
            return DestinationStyleType.Dialog(importable)
        }

        if (animatedStyle != null && animatedStyle!!.isAssignableFrom(ksStyleType)) {
            val importable = ksStyleType.resolveImportable()
                ?: animatedStyle!!.resolveImportable()
                ?: throw IllegalDestinationsSetup("Parameter $DESTINATION_ANNOTATION_STYLE_ARGUMENT of Destination annotation in $locationError was not resolvable: please review it.")
            return DestinationStyleType.Animated(importable, ksStyleType.declaration.findAllRequireOptInAnnotations())
        }

        val importable = ksStyleType.resolveImportable()
            ?: throw IllegalDestinationsSetup("Parameter $DESTINATION_ANNOTATION_STYLE_ARGUMENT of Destination annotation in $locationError was not resolvable: please review it.")

        throw IllegalDestinationsSetup("Unknown style used on $locationError. Please recheck it.")
    }

    // KSP 2.3+ may not return a KSClassDeclaration from findActualClassDeclaration() for
    // nested classes obtained from annotation KClass arguments — fall back to declaration directly.
    private fun KSType.resolveImportable(): Importable? =
        findActualClassDeclaration()?.toImportable()
            ?: declaration.qualifiedName?.let { Importable(declaration.simpleName.asString(), it.asString()) }

    private val defaultStyle by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Default")!!
            .asType(emptyList())
    }

    private val bottomSheetStyle by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.bottomsheet.spec.DestinationStyleBottomSheet")?.asType(emptyList())
    }

    private val animatedStyle by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Animated")?.asType(emptyList())
    }

    private val dialogStyle by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Dialog")!!.asType(emptyList())
    }
}

