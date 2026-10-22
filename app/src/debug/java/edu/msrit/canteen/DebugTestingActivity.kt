package edu.msrit.canteen

/** Separate debug-launcher entry; excluded from release artifacts by source-set and manifest. */
class DebugTestingActivity : MainActivity() {
    override val startInDeveloperHub: Boolean = true
}
