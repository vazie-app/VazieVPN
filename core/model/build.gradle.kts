plugins {
    id("vazie.jvm.library")
}

// No dependencies beyond the Kotlin stdlib, by rule R6: :core:model is the foundation every other
// module may safely depend on, so it must not pull anything into that position.
