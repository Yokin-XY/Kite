package com.kite.app.feature.recipeeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.agent.registration.AgentRegistryDependenciesOwner
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.application.recipes.RecipeFeatureDependenciesOwner
import com.kite.app.application.recipes.RecipeFeatureGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 配方编辑 Feature。草稿、图片、校验和页面生命周期均在模块内部闭环。 */
internal class RecipeEditorFragment : Fragment() {
    private val gateway: RecipeFeatureGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RecipeFeatureDependenciesOwner
            ?: error("Application 必须提供 RecipeFeatureGateway")
        owner.recipeFeatureGateway
    }
    private val agentRegistry: KiteAgentRegistry by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? AgentRegistryDependenciesOwner
            ?: error("Application 必须提供 KiteAgentRegistry")
        owner.agentRegistry
    }
    private val controller: RecipeEditorController by lazy(LazyThreadSafetyMode.NONE) {
        RecipeEditorController(
            gateway,
            initiallyRuntimeBlocked = runtimeBlocked,
            agentEntries = { agentRegistry.snapshot().entries }
        )
    }
    private var screen: RecipeEditorScreen? = null
    private var restoredDraft: RecipeEditorDraft? = null
    private var runtimeBlocked = true
    private var closing = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) openCropDialog(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtimeBlocked = arguments?.getBoolean(ARG_RUNTIME_BLOCKED, true) ?: true
        restoredDraft = savedInstanceState?.getString(STATE_DRAFT)
            ?.let(RecipeEditorDraft::fromJson)
            ?: arguments?.getString(ARG_RESTORED_DRAFT)?.let(RecipeEditorDraft::fromJson)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = RecipeEditorScreen(
        context = requireContext(),
        actions = screenActions,
        iconSources = gateway::customEditorIconSources,
        iconBytes = gateway::readEditorIcon
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect { state -> screen?.render(state) } }
                launch {
                    gateway.changes.collect {
                        controller.dispatch(RecipeEditorAction.ReconcileRun)
                    }
                }
                launch {
                    agentRegistry.signals.collect {
                        controller.dispatch(
                            RecipeEditorAction.ReconcileAgents(agentRegistry.snapshot().entries)
                        )
                    }
                }
                controller.dispatch(RecipeEditorAction.SetRuntimeBlocked(runtimeBlocked))
                if (controller.state.value.phase == RecipeEditorPhase.Idle) {
                    controller.dispatch(
                        RecipeEditorAction.Initialize(
                            recipeId = recipeIdHint().takeIf(String::isNotBlank),
                            restoredDraft = restoredDraft
                        )
                    )?.let(::handleEffect)
                } else {
                    controller.dispatch(RecipeEditorAction.ReconcileRun)
                }
            }
        }
    }

    override fun onPause() {
        if (!closing && controller.state.value.phase != RecipeEditorPhase.Idle) {
            lifecycleScope.launch { controller.dispatch(RecipeEditorAction.PersistDraft) }
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = controller.state.value
        if (state.phase != RecipeEditorPhase.Idle) {
            outState.putString(STATE_DRAFT, state.draft.toJson().toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    fun recipeIdHint(): String = arguments?.getString(ARG_RECIPE_ID).orEmpty()

    fun currentDraftRaw(): String? = controller.state.value
        .takeIf { it.phase != RecipeEditorPhase.Idle }
        ?.draft
        ?.toJson()
        ?.toString()

    fun updateRuntimeBlocked(blocked: Boolean) {
        runtimeBlocked = blocked
        if (isAdded) {
            lifecycleScope.launch {
                controller.dispatch(RecipeEditorAction.SetRuntimeBlocked(blocked))
            }
        }
    }

    fun handleBackRequest() {
        val state = controller.state.value
        if (state.phase == RecipeEditorPhase.Saving || state.phase == RecipeEditorPhase.Deleting) {
            Toast.makeText(requireContext(), "正在处理，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (!state.isDirty) {
            discardAndClose()
            return
        }
        screen?.showUnsavedDialog(
            creatingNew = state.isNew,
            onDiscard = ::discardAndClose,
            onSave = ::save
        )
    }

    private val screenActions = object : RecipeEditorScreenActions {
        override fun onBack() = handleBackRequest()

        override fun onSave() = save()

        override fun onDelete() {
            dispatch(RecipeEditorAction.Delete)
        }

        override fun onNameChanged(value: String) = dispatch(RecipeEditorAction.SetName(value))

        override fun onDescriptionChanged(value: String) =
            dispatch(RecipeEditorAction.SetDescription(value))

        override fun onSelectBuiltinIcon(name: String) =
            dispatch(RecipeEditorAction.SelectBuiltinIcon(name))

        override fun onSelectImageIcon(source: String) =
            dispatch(RecipeEditorAction.SelectImageIcon(source))

        override fun onPickImage() {
            runCatching { imagePicker.launch("image/*") }
                .onFailure {
                    Toast.makeText(requireContext(), "没有可用的图片选择器", Toast.LENGTH_SHORT).show()
                }
        }

        override fun onSelectGroup(groupId: String) =
            dispatch(RecipeEditorAction.SelectGroup(groupId))

        override fun onCreateGroup(name: String) =
            dispatch(RecipeEditorAction.CreateGroup(name))

        override fun onSetLaunchOpenInstance(enabled: Boolean) =
            dispatch(RecipeEditorAction.SetLaunchOpenInstance(enabled))

        override fun onSetKeepFinishedNotification(enabled: Boolean) =
            dispatch(RecipeEditorAction.SetKeepFinishedNotification(enabled))

        override fun onSetShortcutRequested(requested: Boolean) {
            if (requested && !controller.state.value.draft.shortcutRequested) {
                Toast.makeText(requireContext(), "已选择，保存后将申请添加到桌面", Toast.LENGTH_SHORT).show()
            }
            dispatch(RecipeEditorAction.SetShortcutRequested(requested))
        }

        override fun onPutStep(index: Int?, step: RecipeEditorStepDraft) =
            dispatch(RecipeEditorAction.PutStep(index, step))

        override fun onRemoveStep(index: Int) = dispatch(RecipeEditorAction.RemoveStep(index))

        override fun onMoveStep(from: Int, to: Int) =
            dispatch(RecipeEditorAction.MoveStep(from, to))

        override fun onApplyTemplate(type: String) =
            dispatch(RecipeEditorAction.ApplyTemplate(type))

        override fun onOpenRawJson(recipeId: String) = persistThenSend(
            RecipeEditorRequest.OpenRawJson(recipeId)
        )

        override fun onOpenRunHistory(recipeId: String) = persistThenSend(
            RecipeEditorRequest.OpenRunHistory(recipeId)
        )

        override fun onRun(intent: KiteRecipeActionIntent) =
            dispatch(RecipeEditorAction.Run(intent))
    }

    private fun save() {
        dispatch(RecipeEditorAction.Save)
    }

    private fun dispatch(action: RecipeEditorAction) {
        lifecycleScope.launch {
            controller.dispatch(action)?.let(::handleEffect)
        }
    }

    private fun handleEffect(effect: RecipeEditorEffect) {
        when (effect) {
            is RecipeEditorEffect.Saved -> {
                closing = true
                Toast.makeText(requireContext(), "已保存配置", Toast.LENGTH_SHORT).show()
                if (effect.shortcutRequested) {
                    send(RecipeEditorRequest.RequestShortcut(effect.recipeId))
                }
                send(RecipeEditorRequest.Close)
            }
            is RecipeEditorEffect.Deleted -> {
                closing = true
                Toast.makeText(requireContext(), "已删除配置", Toast.LENGTH_SHORT).show()
                send(
                    RecipeEditorRequest.Deleted(
                        recipeId = effect.recipeId,
                        removedCardInstanceIds = effect.removedCardInstanceIds
                    )
                )
            }
            is RecipeEditorEffect.DeleteRequiresStop -> {
                send(RecipeEditorResultContract.actionRequest(effect.request))
                Toast.makeText(requireContext(), "已先请求停止，请停止后再删除配置", Toast.LENGTH_SHORT).show()
            }
            is RecipeEditorEffect.ActionRequested ->
                send(RecipeEditorResultContract.actionRequest(effect.request))
            is RecipeEditorEffect.GroupCreated ->
                Toast.makeText(requireContext(), "已创建分组", Toast.LENGTH_SHORT).show()
            is RecipeEditorEffect.ValidationFailed ->
                Toast.makeText(requireContext(), effect.errors.firstOrNull()?.message ?: "请检查配置", Toast.LENGTH_SHORT).show()
            is RecipeEditorEffect.Failed -> {
                screen?.render(controller.state.value)
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }
            RecipeEditorEffect.DraftPersisted,
            RecipeEditorEffect.DraftDiscarded -> Unit
        }
    }

    private fun discardAndClose() {
        closing = true
        lifecycleScope.launch {
            controller.dispatch(RecipeEditorAction.DiscardDraft)
            send(RecipeEditorRequest.Close)
        }
    }

    private fun persistThenSend(request: RecipeEditorRequest) {
        lifecycleScope.launch {
            controller.dispatch(RecipeEditorAction.PersistDraft)
            send(request)
        }
    }

    private fun openCropDialog(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmap(appContext, uri, MAX_SOURCE_BITMAP_SIZE)
            }
            if (bitmap == null) {
                context?.let { Toast.makeText(it, "无法读取这张图片", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            val hostContext = context
            if (!isAdded || hostContext == null) {
                bitmap.recycle()
                return@launch
            }
            showRecipeEditorIconCropDialog(hostContext, bitmap, ::saveCroppedIcon)
        }
    }

    private fun saveCroppedIcon(bitmap: Bitmap) {
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        lifecycleScope.launch {
            runCatching { gateway.saveEditorIcon(bytes) }
                .onSuccess { source ->
                    controller.dispatch(RecipeEditorAction.SelectImageIcon(source))
                    context?.let { Toast.makeText(it, "已加入头像集", Toast.LENGTH_SHORT).show() }
                }
                .onFailure { error ->
                    context?.let {
                        Toast.makeText(
                            it,
                            "保存头像失败：${error.message ?: error.javaClass.simpleName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun decodeBitmap(context: android.content.Context, uri: Uri, maxSize: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun send(request: RecipeEditorRequest) {
        RecipeEditorResultContract.send(this, request)
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"
        private const val ARG_RESTORED_DRAFT = "restored_draft"
        private const val ARG_RUNTIME_BLOCKED = "runtime_blocked"
        private const val STATE_DRAFT = "recipe_editor_draft"
        private const val MAX_SOURCE_BITMAP_SIZE = 2048

        fun newInstance(
            recipeId: String?,
            restoredDraftRaw: String?,
            runtimeBlocked: Boolean
        ): RecipeEditorFragment = RecipeEditorFragment().apply {
            arguments = Bundle().apply {
                recipeId?.takeIf(String::isNotBlank)?.let { putString(ARG_RECIPE_ID, it) }
                restoredDraftRaw?.takeIf(String::isNotBlank)?.let { putString(ARG_RESTORED_DRAFT, it) }
                putBoolean(ARG_RUNTIME_BLOCKED, runtimeBlocked)
            }
        }
    }
}
