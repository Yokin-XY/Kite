package com.kite.app.feature.recipeeditor

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentDefinition
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentLaunchStatus
import com.kite.app.agent.registration.AgentRegistration
import com.kite.app.agent.registration.AgentRegistrationSource
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.agent.registration.AgentRuntimeStatus
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteCardRunUiProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class RecipeEditorScreenTest {
    @Test
    fun syntheticIdleRunDoesNotPretendToBeRunHistory() {
        val screen = screen(RecordingActions())
        attach(screen)

        screen.render(state())

        assertTrue(screen.root.allText().contains(screen.root.context.getString(R.string.recipe_editor_no_runs)))
    }

    @Test
    fun stepMoveControlSubmitsOrderedDraftAction() {
        val actions = RecordingActions()
        val screen = screen(actions)
        attach(screen)
        screen.render(state())

        screen.root.findByDescription(
            screen.root.context.getString(R.string.recipe_editor_move_down)
        )!!.performClick()

        assertEquals(listOf(0 to 1), actions.moves)
    }

    @Test
    fun `editor chrome uses standard actions without text glyph navigation`() {
        val screen = screen(RecordingActions())
        attach(screen)
        screen.render(state())

        val texts = screen.root.allText()
        val context = screen.root.context
        val addAction = context.getString(R.string.recipe_editor_add_action)
        assertTrue(texts.contains(context.getString(R.string.recipe_editor_edit_title)))
        assertTrue(texts.contains(addAction))
        assertTrue(texts.none { it.startsWith("+") && it.contains(addAction) })
        assertTrue(screen.root.findByDescription(context.getString(R.string.common_back)) != null)
        assertTrue(screen.root.findByDescription(context.getString(R.string.recipe_editor_more)) != null)
    }

    @Test
    @Config(qualifiers = "en")
    fun `empty category uses localized ungrouped label`() {
        val screen = screen(RecordingActions())
        attach(screen)
        screen.render(state())

        assertTrue(screen.root.allText().contains("Ungrouped"))
    }

    @Test
    fun fourthActionLetsUserChooseRegisteredAgentByFriendlyName() {
        val actions = RecordingActions()
        val screen = screen(actions)
        attach(screen)
        screen.render(state().copy(agents = listOf(agentEntry())))
        val context = screen.root.context

        screen.root.findByDescription(context.getString(R.string.recipe_editor_add_action))!!.performClick()
        val dialogRoot = ShadowDialog.getLatestDialog().window!!.decorView
        dialogRoot.findByText("✦ ${context.getString(R.string.recipe_editor_step_agent)}")!!.performClick()
        dialogRoot.findByDescription(
            context.getString(R.string.recipe_editor_agent_choice_description, "OpenCode")
        )!!.performClick()
        dialogRoot.findByText(context.getString(R.string.recipe_editor_add))!!.performClick()

        assertEquals("opencode", actions.putSteps.single().second.agentId)
        assertEquals(KiteRecipe.STEP_AGENT, actions.putSteps.single().second.type)
    }

    private fun screen(actions: RecordingActions): RecipeEditorScreen {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return RecipeEditorScreen(
            context = ContextThemeWrapper(activity, R.style.Theme_Kite),
            actions = actions,
            iconSources = { emptyList() },
            iconBytes = { null }
        )
    }

    private fun attach(screen: RecipeEditorScreen) {
        val activity = (screen.root.context as ContextThemeWrapper).baseContext as Activity
        activity.setContentView(screen.root)
    }

    private fun state(): RecipeEditorUiState {
        val recipe = recipe()
        val idle = CardRunState.fromRecipeStatus(recipe.id, "unknown")
        return RecipeEditorUiState(
            phase = RecipeEditorPhase.Ready,
            originalRecipe = recipe,
            baseline = RecipeEditorDraft.fromRecipe(recipe),
            draft = RecipeEditorDraft.fromRecipe(recipe),
            run = idle,
            runProjection = KiteCardRunUiProjector.project(idle.status),
            runtimeBlocked = false
        )
    }

    private fun recipe(): KiteRecipe = KiteRecipe(
        id = "tool",
        name = "Tool",
        description = "Test recipe",
        type = KiteRecipe.TYPE_COMMAND_WEB,
        defaultUrl = "https://example.com",
        shortcut = false,
        launch = KiteLaunchConfig(openInstance = true),
        execution = KiteExecution.steps(
            listOf(
                KiteRecipeStep(id = "shell", type = KiteRecipe.STEP_SHELL, cmd = "echo ok"),
                KiteRecipeStep(id = "web", type = KiteRecipe.STEP_OPEN_WEB, url = "https://example.com")
            )
        )
    )

    private fun View.allText(): List<String> = buildList {
        if (this@allText is TextView) add(text.toString())
        if (this@allText is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).allText())
        }
    }

    private fun View.findByDescription(description: String): View? {
        if (contentDescription?.toString() == description && isEnabled) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findByDescription(description)?.let { return it }
            }
        }
        return null
    }

    private fun View.findByText(value: String): View? {
        if (this is TextView && text.toString() == value && isEnabled) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findByText(value)?.let { return it }
            }
        }
        return null
    }

    private fun agentEntry(): AgentRegistryEntry = AgentRegistryEntry(
        registration = AgentRegistration(
            definition = AgentDefinition("opencode", "OpenCode"),
            source = AgentRegistrationSource.Custom,
            launch = AgentLaunchSpec.Managed("opencode", "acp", "stdio", listOf("opencode", "acp"))
        ),
        installationStatus = AgentInstallationStatus.NotApplicable,
        configurationStatus = AgentConfigurationStatus.NotRequired,
        runtimeStatus = AgentRuntimeStatus.Stopped,
        launchStatus = AgentLaunchStatus.Ready
    )

    private class RecordingActions : RecipeEditorScreenActions {
        val moves = mutableListOf<Pair<Int, Int>>()
        val putSteps = mutableListOf<Pair<Int?, RecipeEditorStepDraft>>()

        override fun onBack() = Unit
        override fun onSave() = Unit
        override fun onDelete() = Unit
        override fun onNameChanged(value: String) = Unit
        override fun onDescriptionChanged(value: String) = Unit
        override fun onSelectBuiltinIcon(name: String) = Unit
        override fun onSelectImageIcon(source: String) = Unit
        override fun onPickImage() = Unit
        override fun onSelectGroup(groupId: String) = Unit
        override fun onCreateGroup(name: String) = Unit
        override fun onSetLaunchOpenInstance(enabled: Boolean) = Unit
        override fun onSetKeepFinishedNotification(enabled: Boolean) = Unit
        override fun onSetShortcutRequested(requested: Boolean) = Unit
        override fun onPutStep(index: Int?, step: RecipeEditorStepDraft) {
            putSteps += index to step
        }
        override fun onRemoveStep(index: Int) = Unit
        override fun onMoveStep(from: Int, to: Int) {
            moves += from to to
        }
        override fun onApplyTemplate(type: String) = Unit
        override fun onOpenRawJson(recipeId: String) = Unit
        override fun onOpenRunHistory(recipeId: String) = Unit
        override fun onRun(intent: KiteRecipeActionIntent) = Unit
    }
}
