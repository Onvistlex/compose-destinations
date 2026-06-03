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

        if (defaultStyleDecl.asType(emptyList()).isAssignableFrom(ksStyleType)) {
            return DestinationStyleType.Default
        }

        if (bottomSheetStyleDecl != null && bottomSheetStyleDecl!!.asType(emptyList()).isAssignableFrom(ksStyleType)) {
            return DestinationStyleType.BottomSheet
        }

        // Check known styles BEFORE resolving importable — in KSP 2.3+ the KSType obtained
        // from annotation KClass arguments may have a KSTypeParameter as declaration (null
        // qualifiedName), so we must identify the style via isAssignableFrom first.
        if (dialogStyleDecl.asType(emptyList()).isAssignableFrom(ksStyleType)) {
            // Try to resolve the actual subclass importable; if KSP 2.3+ can't give us a
            // KSClassDeclaration, fall back to the known Dialog declaration directly.
            val importable = ksStyleType.resolveImportable()
                ?: dialogStyleDecl.toImportable()
                ?: throw IllegalDestinationsSetup("Parameter $DESTINATION_ANNOTATION_STYLE_ARGUMENT of Destination annotation in $locationError was not resolvable: please review it.")
            return DestinationStyleType.Dialog(importable)
        }

        if (animatedStyleDecl != null && animatedStyleDecl!!.asType(emptyList()).isAssignableFrom(ksStyleType)) {
            val importable = ksStyleType.resolveImportable()
                ?: animatedStyleDecl!!.toImportable()
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

    // Cache KSClassDeclarations directly so we can use toImportable() as a reliable fallback
    // without going through KSType.declaration (which may be unreliable in KSP 2.3+).
    private val defaultStyleDecl by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Default")!!
    }

    private val bottomSheetStyleDecl by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.bottomsheet.spec.DestinationStyleBottomSheet")
    }

    private val animatedStyleDecl by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Animated")
    }

    private val dialogStyleDecl by lazy {
        resolver.getClassDeclarationByName("$CORE_PACKAGE_NAME.spec.DestinationStyle.Dialog")!!
    }

    // Keep KSType-based lazy vals for backwards-compat usage in the rest of the codebase
    private val defaultStyle by lazy { defaultStyleDecl.asType(emptyList()) }
    private val bottomSheetStyle by lazy { bottomSheetStyleDecl?.asType(emptyList()) }
    private val animatedStyle by lazy { animatedStyleDecl?.asType(emptyList()) }
    private val dialogStyle by lazy { dialogStyleDecl.asType(emptyList()) }
}
