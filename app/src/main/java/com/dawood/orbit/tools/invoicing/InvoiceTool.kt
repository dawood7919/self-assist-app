package com.dawood.orbit.tools.invoicing

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitBottomSheet
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Invoice Studio — quotes and invoices as print-ready PDFs, with the client
 * address book and business header kept on device.
 *
 * The editor edits a working copy only; nothing is persisted until Save or
 * Export, so browsing other documents can never leave a half-edited row on
 * disk. All money math happens in [InvoiceMath] (BigDecimal); the UI only
 * holds strings and displays the recomputed totals.
 */
@Composable
fun InvoiceTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invoicesRepository = remember(context) { InvoicesRepository.get(context) }
    val clientsRepository = remember(context) { ClientsRepository.get(context) }
    val profileStore = remember(context) { BusinessProfileStore.get(context) }

    val documents by invoicesRepository.items.collectAsStateWithLifecycle()
    val clients by clientsRepository.items.collectAsStateWithLifecycle()
    val profile by profileStore.profile.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("All") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var clientsSheetOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var publishNote by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val ordered = remember(documents) {
        documents.sortedByDescending { it.updatedAt }
    }
    val visible = remember(ordered, filter) {
        when (filter) {
            "Drafts" -> ordered.filter { it.status == DocumentStatus.Draft }
            "Sent" -> ordered.filter { it.status == DocumentStatus.Sent }
            "Paid" -> ordered.filter { it.status == DocumentStatus.Paid }
            "Quotes" -> ordered.filter { it.kind == DocumentKind.Quote }
            else -> ordered
        }
    }
    val selected = documents.firstOrNull { it.id == selectedId }
    val editor = remember(selectedId) { selected?.let { EditorState(it) } }

    fun persist(snapshot: InvoiceDocument) {
        invoicesRepository.save(snapshot)
        selectedId = snapshot.id
    }

    fun exportPdf(snapshot: InvoiceDocument) {
        exporting = true
        exportError = null
        invoicesRepository.save(snapshot)
        selectedId = snapshot.id
        scope.launch {
            when (val result = InvoicePdf.render(context, snapshot, profile)) {
                is InvoicePdf.Result.Success -> {
                    exportedFile = result.file
                    publishNote = null
                }
                is InvoicePdf.Result.Failure -> exportError = result.message
            }
            exporting = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = selected?.let { "${it.number} · ${InvoiceMath.formatMoney(it.totals.total, it.currency)}" },
        settingsTitle = "Business profile",
        settingsContent = { BusinessProfileSettings(profile, onSave = profileStore::save) },
        panel = ToolPanel(title = "Documents", icon = OrbitIcons.Print) {
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                OrbitButton(
                    text = "Invoice",
                    onClick = {
                        val doc = invoicesRepository.create(DocumentKind.Invoice)
                        selectedId = doc.id
                        exportedFile = null
                    },
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Add,
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Quote",
                    onClick = {
                        val doc = invoicesRepository.create(DocumentKind.Quote)
                        selectedId = doc.id
                        exportedFile = null
                    },
                    size = OrbitButtonSize.Small,
                    variant = OrbitButtonVariant.Secondary,
                    leadingIcon = OrbitIcons.Add,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                listOf("All", "Drafts", "Sent", "Paid", "Quotes").forEach { label ->
                    OrbitChip(
                        text = label,
                        selected = filter == label,
                        onClick = { filter = label },
                    )
                }
            }
            // The document list scrolls inside the side panel while the
            // create buttons and filters stay pinned.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (visible.isEmpty()) {
                    OrbitText(
                        "No documents here yet.",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                        modifier = Modifier.padding(OrbitTheme.spacing.md),
                    )
                }
                visible.forEach { doc ->
                val overdue = InvoiceMath.isOverdue(doc, System.currentTimeMillis())
                OrbitListItem(
                    title = "${doc.number} · ${doc.clientCompany.ifBlank { doc.clientName.ifBlank { "Untitled" } }}",
                    subtitle = InvoiceMath.formatMoney(doc.totals.total, doc.currency) +
                        if (overdue) " · overdue" else "",
                    selected = doc.id == selectedId,
                    onClick = {
                        selectedId = doc.id
                        exportedFile = null
                        exportError = null
                    },
                    trailing = {
                        OrbitBadge(
                            when {
                                doc.kind == DocumentKind.Quote && doc.status == DocumentStatus.Paid -> "Accepted"
                                overdue -> "Overdue"
                                else -> doc.status.label
                            },
                            tone = when {
                                doc.status == DocumentStatus.Paid -> OrbitTone.Success
                                overdue -> OrbitTone.Error
                                doc.status == DocumentStatus.Sent -> OrbitTone.Info
                                else -> OrbitTone.Neutral
                            },
                        )
                    },
                )
                }
            }
        },
        bottomBar = {
            if (editor != null && selected != null) {
                OrbitButton(
                    text = "Delete",
                    onClick = {
                        invoicesRepository.remove(selected.id)
                        selectedId = null
                        exportedFile = null
                    },
                    variant = OrbitButtonVariant.Ghost,
                    leadingIcon = OrbitIcons.Delete,
                )
                Spacer(Modifier.weight(1f))
                OrbitButton(
                    text = "Save",
                    onClick = { persist(editor.toDocument(selected)) },
                    variant = OrbitButtonVariant.Secondary,
                    leadingIcon = OrbitIcons.Save,
                )
                OrbitButton(
                    text = "Export PDF",
                    onClick = { exportPdf(editor.toDocument(selected)) },
                    leadingIcon = OrbitIcons.Print,
                    loading = exporting,
                )
            }
        },
    ) {
        if (editor == null || selected == null) {
            OrbitEmptyState(
                title = "No document open",
                description = "Create an invoice or a quote, or pick one from the list. " +
                    "Everything is stored on this device and exported as a print-ready PDF.",
                icon = OrbitIcons.Print,
                primaryActionLabel = "New invoice",
                onPrimaryAction = {
                    val doc = invoicesRepository.create(DocumentKind.Invoice)
                    selectedId = doc.id
                },
            )
        } else {
            InvoiceEditor(
                editor = editor,
                documents = documents,
                clients = clients,
                onPickClient = { client ->
                    editor.applyClient(client)
                    clientsSheetOpen = false
                },
                onSaveClient = { client -> clientsRepository.upsertClient(client) },
                clientsSheetOpen = clientsSheetOpen,
                onClientsSheetChange = { clientsSheetOpen = it },
                exportError = exportError,
                exportedFile = exportedFile,
                publishNote = publishNote,
                onPublish = { publishNote = it },
            )
        }
    }
}

@Composable
private fun InvoiceEditor(
    editor: EditorState,
    documents: List<InvoiceDocument>,
    clients: List<Client>,
    onPickClient: (Client) -> Unit,
    onSaveClient: (Client) -> Unit,
    clientsSheetOpen: Boolean,
    onClientsSheetChange: (Boolean) -> Unit,
    exportError: String?,
    exportedFile: File?,
    publishNote: String?,
    onPublish: (String) -> Unit,
) {
    val preview = remember(editor.revision) { editor.toDocument(null) }
    val totals = preview.totals
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(OrbitTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                ToolWorkspace(label = "Document") {
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                        DocumentKind.entries.forEach { kind ->
                            OrbitChip(
                                text = if (kind == DocumentKind.Quote) "Quote" else "Invoice",
                                selected = editor.kind == kind,
                                onClick = {
                                    val old = editor.kind
                                    if (old != kind && editor.number.startsWith(old.prefix + "-")) {
                                        editor.number = InvoiceMath.nextNumber(documents, kind)
                                    }
                                    editor.kind = kind
                                    editor.bump()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                        DocumentStatus.entries.forEach { status ->
                            val label = if (status == DocumentStatus.Paid &&
                                editor.kind == DocumentKind.Quote
                            ) "Accepted" else status.label
                            OrbitChip(
                                text = label,
                                selected = editor.status == status,
                                onClick = {
                                    editor.status = status
                                    editor.bump()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        OrbitTextField(
                            value = editor.number,
                            onValueChange = { editor.number = it },
                            label = "Number",
                            modifier = Modifier.weight(1f),
                        )
                        OrbitTextField(
                            value = editor.currency,
                            onValueChange = { editor.currency = it.uppercase().take(3) },
                            label = "Currency",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        DateField(
                            label = "Issue date",
                            millis = editor.issueDate,
                            onChange = { editor.issueDate = it },
                            modifier = Modifier.weight(1f),
                        )
                        DateField(
                            label = "Due date (optional)",
                            millis = editor.dueDate,
                            onChange = { editor.dueDate = it },
                            onClear = { editor.dueDate = null },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                ToolWorkspace(
                    label = "Bill to",
                    toolbar = {
                        OrbitButton(
                            text = "Clients",
                            onClick = { onClientsSheetChange(true) },
                            variant = OrbitButtonVariant.Tertiary,
                            leadingIcon = OrbitIcons.Person,
                            size = OrbitButtonSize.Small,
                        )
                    },
                ) {
                    OrbitTextField(
                        value = editor.clientName,
                        onValueChange = { editor.clientName = it },
                        label = "Contact name",
                    )
                    OrbitTextField(
                        value = editor.clientCompany,
                        onValueChange = { editor.clientCompany = it },
                        label = "Company",
                    )
                    OrbitTextField(
                        value = editor.clientAddress,
                        onValueChange = { editor.clientAddress = it },
                        label = "Address",
                        singleLine = false,
                        minLines = 2,
                    )
                    OrbitTextField(
                        value = editor.clientTaxId,
                        onValueChange = { editor.clientTaxId = it },
                        label = "Tax ID / TRN (optional)",
                    )
                    OrbitButton(
                        text = "Save as client",
                        onClick = {
                            val client = Client(
                                id = editor.clientId ?: java.util.UUID.randomUUID().toString(),
                                name = editor.clientName,
                                company = editor.clientCompany,
                                address = editor.clientAddress,
                                taxId = editor.clientTaxId,
                            )
                            onSaveClient(client)
                            editor.clientId = client.id
                        },
                        variant = OrbitButtonVariant.Secondary,
                        leadingIcon = OrbitIcons.Save,
                    )
                }

                ToolWorkspace(label = "Items") {
                    editor.items.forEachIndexed { index, item ->
                        ItemRowEditor(
                            item = item,
                            onChange = { editor.bump() },
                            onRemove = {
                                editor.items.removeAt(index)
                                editor.bump()
                            },
                        )
                    }
                    OrbitButton(
                        text = "Add item",
                        onClick = { editor.items.add(ItemDraft()) },
                        variant = OrbitButtonVariant.Tertiary,
                        leadingIcon = OrbitIcons.Add,
                    )
                }

                ToolWorkspace(label = "Totals") {
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        MoneyField("Discount %", value = editor.discountPct,
                            onChange = { editor.discountPct = it; editor.bump() },
                            modifier = Modifier.weight(1f))
                        MoneyField("Discount fixed", value = editor.discountFixed,
                            onChange = { editor.discountFixed = it; editor.bump() },
                            modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        MoneyField("Tax %", value = editor.taxPct,
                            onChange = { editor.taxPct = it; editor.bump() },
                            modifier = Modifier.weight(1f))
                        MoneyField("Shipping", value = editor.shipping,
                            onChange = { editor.shipping = it; editor.bump() },
                            modifier = Modifier.weight(1f))
                    }
                    MoneyField("Amount already paid", value = editor.amountPaid,
                        onChange = { editor.amountPaid = it; editor.bump() })
                    TotalLine("Subtotal", totals.subtotal, editor.currency)
                    if (totals.discount.signum() > 0) {
                        TotalLine("Discount", totals.discount.negate(), editor.currency, muted = true)
                    }
                    TotalLine("Tax (${trimDouble(preview.taxPct)}%)", totals.tax, editor.currency)
                    if (totals.shipping.signum() > 0) {
                        TotalLine("Shipping", totals.shipping, editor.currency)
                    }
                    TotalLine("TOTAL", totals.total, editor.currency, strong = true)
                    if (totals.paid.signum() > 0) {
                        TotalLine("Paid", totals.paid.negate(), editor.currency, muted = true)
                        TotalLine("Balance due", totals.balanceDue, editor.currency, strong = true)
                    }
                }

                ToolWorkspace(label = "Notes & terms") {
                    OrbitTextField(
                        value = editor.notes,
                        onValueChange = { editor.notes = it },
                        label = "Notes shown on the PDF",
                        singleLine = false,
                        minLines = 2,
                    )
                    OrbitTextField(
                        value = editor.terms,
                        onValueChange = { editor.terms = it },
                        label = "Terms",
                        singleLine = false,
                        minLines = 3,
                    )
                }

                exportError?.let {
                    ToolWorkspace(color = OrbitTheme.colors.errorSubtle) {
                        OrbitText(
                            it,
                            style = OrbitTheme.typography.bodySmall,
                            color = OrbitTheme.colors.error,
                        )
                    }
                }
                exportedFile?.let { file ->
                    FileResult(
                        file = DocumentStore.describe(
                            file = file,
                            state = FileState.Completed,
                            meta = preview.number,
                        ),
                        title = "PDF ready",
                    ) {
                        OrbitButton(
                            text = "Open",
                            onClick = { DocumentStore.open(context, file) },
                            leadingIcon = OrbitIcons.OpenExternal,
                        )
                        OrbitButton(
                            text = "Share",
                            onClick = { DocumentStore.share(context, file) },
                            variant = OrbitButtonVariant.Secondary,
                            leadingIcon = OrbitIcons.Share,
                        )
                        OrbitButton(
                            text = "Save to Downloads",
                            onClick = {
                                onPublish(
                                    DocumentStore.publish(context, file)
                                        ?.let { "Saved to $it" }
                                        ?: "Kept in the app's files — share it to move it out",
                                )
                            },
                            variant = OrbitButtonVariant.Ghost,
                            leadingIcon = OrbitIcons.Download,
                        )
                    }
                    publishNote?.let {
                        OrbitText(it, style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted)
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    ToolFooter(
                        "PDFs are generated on this device. The bundled Helvetica font " +
                            "prints Latin text; non-Latin characters are substituted.",
                    )
                }
            }
        }
    }

    OrbitBottomSheet(
        visible = clientsSheetOpen,
        onDismiss = { onClientsSheetChange(false) },
        title = "Clients",
        subtitle = "Pick a saved bill-to address",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        ) {
        if (clients.isEmpty()) {
            OrbitText(
                "No saved clients yet. Use “Save as client” in the Bill-to card.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textMuted,
            )
        }
        clients.sortedBy { it.displayName.lowercase() }.forEach { client ->
            OrbitListItem(
                title = client.displayName,
                subtitle = listOf(client.name, client.taxId).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { null },
                onClick = { onPickClient(client) },
            )
        }
        }
    }
}

@Composable
private fun ItemRowEditor(
    item: ItemDraft,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val amount = remember(item.qty, item.unitPrice) {
        val qty = InvoiceMath.parseAmount(item.qty)?.toDouble() ?: 0.0
        val price = InvoiceMath.parseAmount(item.unitPrice)?.toDouble() ?: 0.0
        InvoiceMath.money(qty * price)
    }
    ToolWorkspace {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitTextField(
                value = item.description,
                onValueChange = { item.description = it },
                label = "Description",
                modifier = Modifier.weight(1f),
            )
            OrbitIconButton(
                icon = OrbitIcons.Delete,
                contentDescription = "Remove item",
                onClick = onRemove,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitTextField(
                value = item.qty,
                onValueChange = { item.qty = it; onChange() },
                label = "Qty",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(0.7f),
            )
            OrbitTextField(
                value = item.unit,
                onValueChange = { item.unit = it },
                label = "Unit",
                modifier = Modifier.weight(0.8f),
            )
            OrbitTextField(
                value = item.unitPrice,
                onValueChange = { item.unitPrice = it; onChange() },
                label = "Unit price",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1.1f),
            )
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(top = OrbitTheme.spacing.sm),
            ) {
                OrbitText("Amount", style = OrbitTheme.typography.labelSmall,
                    color = OrbitTheme.colors.textMuted)
                OrbitText(amount.toPlainString(), style = OrbitTheme.typography.h4)
            }
        }
    }
}

@Composable
private fun MoneyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun TotalLine(
    label: String,
    value: java.math.BigDecimal,
    currency: String,
    strong: Boolean = false,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OrbitText(
            label,
            style = if (strong) OrbitTheme.typography.h4 else OrbitTheme.typography.bodySmall,
            color = when {
                strong -> OrbitTheme.colors.textPrimary
                muted -> OrbitTheme.colors.textMuted
                else -> OrbitTheme.colors.textSecondary
            },
        )
        OrbitText(
            InvoiceMath.formatMoney(value, currency),
            style = if (strong) OrbitTheme.typography.h3 else OrbitTheme.typography.body,
            color = if (strong) OrbitTheme.colors.accent else OrbitTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun DateField(
    label: String,
    millis: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val format = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val calendar = Calendar.getInstance()
    millis?.let { calendar.timeInMillis = it }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        // Invisible click layer over the field itself; the clear affordance
        // stays OUTSIDE the layer so it never gets intercepted.
        Box(Modifier.weight(1f)) {
            OrbitTextField(
                value = millis?.let { format.format(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = label,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                Calendar.getInstance().apply {
                                    set(year, month, day, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                    onChange(timeInMillis)
                                }
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
            )
        }
        if (onClear != null && millis != null) {
            OrbitIconButton(
                icon = OrbitIcons.Clear,
                contentDescription = "Clear date",
                size = OrbitButtonSize.Small,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun BusinessProfileSettings(
    profile: BusinessProfile,
    onSave: (BusinessProfile) -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) },
            label = "Business name")
        OrbitTextField(value = draft.tagline, onValueChange = { draft = draft.copy(tagline = it) },
            label = "Tagline")
        OrbitTextField(value = draft.address, onValueChange = { draft = draft.copy(address = it) },
            label = "Address (one line per row)", singleLine = false, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitTextField(value = draft.phone, onValueChange = { draft = draft.copy(phone = it) },
                label = "Phone", modifier = Modifier.weight(1f))
            OrbitTextField(value = draft.email, onValueChange = { draft = draft.copy(email = it) },
                label = "Email", modifier = Modifier.weight(1f))
        }
        OrbitTextField(value = draft.taxId, onValueChange = { draft = draft.copy(taxId = it) },
            label = "Tax ID / TRN")
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitTextField(value = draft.currency,
                onValueChange = { draft = draft.copy(currency = it.uppercase().take(3)) },
                label = "Currency", modifier = Modifier.weight(1f))
            OrbitTextField(value = draft.taxPct.toCleanString(),
                onValueChange = { v ->
                    draft = draft.copy(taxPct = v.toDoubleOrNull() ?: draft.taxPct)
                },
                label = "Default tax %",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f))
        }
        OrbitSectionHeader(title = "Bank details (optional)")
        OrbitTextField(value = draft.bankName, onValueChange = { draft = draft.copy(bankName = it) },
            label = "Bank")
        OrbitTextField(value = draft.accountName,
            onValueChange = { draft = draft.copy(accountName = it) }, label = "Account name")
        OrbitTextField(value = draft.iban, onValueChange = { draft = draft.copy(iban = it) },
            label = "IBAN")
        OrbitTextField(value = draft.terms, onValueChange = { draft = draft.copy(terms = it) },
            label = "Default terms", singleLine = false, minLines = 2)
        OrbitButton(text = "Save profile", onClick = { onSave(draft) },
            leadingIcon = OrbitIcons.Save, fullWidth = true)
    }
}

// ------------------------------------------------------------------
// Editor working copy
// ------------------------------------------------------------------

class ItemDraft(
    description: String = "",
    qty: String = "1",
    unit: String = "",
    unitPrice: String = "0",
) {
    var description by mutableStateOf(description)
    var qty by mutableStateOf(qty)
    var unit by mutableStateOf(unit)
    var unitPrice by mutableStateOf(unitPrice)

    fun toLineItem(id: String? = null): LineItem = LineItem(
        id = id ?: java.util.UUID.randomUUID().toString(),
        description = description.trim(),
        qty = InvoiceMath.parseAmount(qty)?.toDouble() ?: 0.0,
        unit = unit.trim(),
        unitPrice = InvoiceMath.parseAmount(unitPrice)?.toDouble() ?: 0.0,
    )

    companion object {
        fun from(item: LineItem) = ItemDraft(
            description = item.description,
            qty = trimDouble(item.qty),
            unit = item.unit,
            unitPrice = trimDouble(item.unitPrice),
        )
    }
}

class EditorState(doc: InvoiceDocument) {
    var kind by mutableStateOf(doc.kind)
    var status by mutableStateOf(doc.status)
    var number by mutableStateOf(doc.number)
    var clientId by mutableStateOf(doc.clientId)
    var clientName by mutableStateOf(doc.clientName)
    var clientCompany by mutableStateOf(doc.clientCompany)
    var clientAddress by mutableStateOf(doc.clientAddress)
    var clientTaxId by mutableStateOf(doc.clientTaxId)
    var issueDate by mutableStateOf(doc.issueDate)
    var dueDate by mutableStateOf(doc.dueDate)
    var currency by mutableStateOf(doc.currency)
    val items = mutableStateListOf<ItemDraft>().apply {
        addAll(doc.items.map { ItemDraft.from(it) })
    }
    var discountPct by mutableStateOf(trimDouble(doc.discountPct))
    var discountFixed by mutableStateOf(trimDouble(doc.discountFixed))
    var taxPct by mutableStateOf(trimDouble(doc.taxPct))
    var shipping by mutableStateOf(trimDouble(doc.shipping))
    var amountPaid by mutableStateOf(trimDouble(doc.amountPaid))
    var notes by mutableStateOf(doc.notes)
    var terms by mutableStateOf(doc.terms)

    /** Bumped on structural edits so `remember(editor.revision)` recomputes. */
    var revision by mutableStateOf(0)
        private set

    fun bump() { revision += 1 }

    fun applyClient(client: Client) {
        clientId = client.id
        clientName = client.name
        clientCompany = client.company
        clientAddress = client.address
        clientTaxId = client.taxId
        bump()
    }

    /**
     * Builds the document to persist. When [original] is null the state is
     * used only for live totals (e.g. before the first save).
     */
    fun toDocument(original: InvoiceDocument?): InvoiceDocument {
        val lines = items.mapIndexed { index, draft ->
            // Preserve ids on already-persisted rows so saves update in place.
            draft.toLineItem(original?.items?.getOrNull(index)?.id)
        }
        val base = original ?: InvoiceDocument(kind = kind, number = number)
        return base.copy(
            kind = kind,
            status = status,
            number = number,
            clientId = clientId,
            clientName = clientName.trim(),
            clientCompany = clientCompany.trim(),
            clientAddress = clientAddress.trim(),
            clientTaxId = clientTaxId.trim(),
            issueDate = issueDate,
            dueDate = dueDate,
            currency = currency.ifBlank { "AED" },
            items = lines,
            discountPct = InvoiceMath.parseAmount(discountPct)?.toDouble() ?: 0.0,
            discountFixed = InvoiceMath.parseAmount(discountFixed)?.toDouble() ?: 0.0,
            taxPct = InvoiceMath.parseAmount(taxPct)?.toDouble() ?: 0.0,
            shipping = InvoiceMath.parseAmount(shipping)?.toDouble() ?: 0.0,
            amountPaid = InvoiceMath.parseAmount(amountPaid)?.toDouble() ?: 0.0,
            notes = notes.trim(),
            terms = terms.trim(),
        )
    }
}

/** 1 → "1", 1.5 → "1.5" — never scientific notation, never trailing zeros. */
private fun trimDouble(value: Double): String {
    val bd = java.math.BigDecimal(value.toString()).stripTrailingZeros()
    return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
}

private fun Double.toCleanString(): String = trimDouble(this)
