# VibeCraftMod UI Schema Specification

**Document Version:** 1.0  
**Based on Code Analysis of:** VibeCraftMod client-side rendering system  
**Key Files:** ScreenDef.java, SchemaScreen.java, SchemaConfig.java, ModPackets.java, WidgetRenderer.java

---

## 1. Root Schema Structure

The schema is a JSON object received from the server via `VibeCraftEventPayload`. The complete root structure:

```json
{
  "defaultPlugin": "vibecraft",
  "title": "VibeCraft Chat",
  "panel": { /* panel configuration */ },
  "widgets": [ /* array of widgets */ ],
  "screens": [ /* array of screen definitions */ ],
  "config": { /* global configuration */ },
  "overlays": [ /* array of overlay definitions */ ]
}
```

### Root-Level Fields

| Field | Type | Purpose | Example |
|-------|------|---------|---------|
| `defaultPlugin` | string | Default plugin when no screen is active | `"vibecraft"` |
| `title` | string | Default screen title | `"VibeCraft Chat"` |
| `panel` | object | Default panel styling (see Panel Config section) | `{...}` |
| `widgets` | array | Default widgets array (fallback) | `[...]` |
| `screens` | array | Array of ScreenDef objects | `[...]` |
| `config` | object | Global config (keybinds, colors, settings) | `{...}` |
| `overlays` | array | Array of HUD overlay definitions | `[...]` |

---

## 2. Screen Definition Structure

Each screen object in the `screens` array:

```json
{
  "id": "vibecraft:chat",
  "plugin": "vibecraft",
  "title": "VibeCraft Chat",
  "priority": 100,
  "panel": { /* panel-specific overrides */ },
  "widgets": [ /* screen-specific widgets */ ],
  "config": { /* screen-specific config */ }
}
```

### ScreenDef Fields (Parsed by ScreenDef.fromJson())

| Field | Type | Optional | Purpose |
|-------|------|----------|---------|
| `id` | string | Required | Unique screen identifier (format: `"plugin:name"`) |
| `plugin` | string | Required | Plugin namespace (used for settings, colors, events) |
| `title` | string | Required | Display title in screen header |
| `priority` | integer | Yes (default: 0) | Higher priority screens shown first in switcher |
| `panel` | object | Yes | Screen-specific panel styling (overrides root panel) |
| `widgets` | array | Yes | Screen-specific widgets (overrides root widgets) |
| `config` | object | Yes | Screen-specific configuration |

**Notes:**
- Screens are sorted by priority (highest first) in `SchemaConfig.loadScreens()`
- If a screen's panel/widgets are empty, root-level panel/widgets are used as fallback

---

## 3. Panel Configuration

Panel objects control the overall UI layout and styling:

```json
{
  "maxWidth": 520,
  "widthPercent": 0.70,
  "padding": 8,
  "titleHeight": 14,
  "background": "#0A0A0F",
  "titleBackground": "#111118",
  "divider": "#2A2A3A",
  "label": "#CCCCCC"
}
```

### Panel Fields

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `maxWidth` | integer | 520 | Maximum panel width in pixels |
| `widthPercent` | float | 0.70 | Panel width as percentage of screen (0.0 - 1.0) |
| `padding` | integer | 8 | Internal padding around content |
| `titleHeight` | integer | 14 | Height of title bar |
| `background` | color | `#0A0A0F` | Panel background color (ARGB hex) |
| `titleBackground` | color | `#111118` | Title bar background color |
| `divider` | color | `#2A2A3A` | Divider line color |
| `label` | color | `#CCCCCC` | Default label text color |

**Actual Panel Width Calculation (from SchemaScreen.render()):**
```java
int panelW = Math.min(
  intVal(panel, "maxWidth", 520), 
  (int) (width * doubleVal(panel, "widthPercent", 0.70))
);
```

---

## 4. Widget Types and Properties

The client supports these widget types (registered in `SchemaScreen.registerDefaultWidgetRenderers()`).
The list below covers all types that have an explicit renderer. Unknown types are silently skipped.

### 4.1 toolbar
**Purpose:** Display toolbar with close, help, options buttons and status text

**Render Method:** `renderToolbar()`

**Required Properties:**
- `type`: `"toolbar"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 14 | Height in pixels |
| `showClose` | boolean | true | Show close button |
| `showOptions` | boolean | true | Show options button |
| `showHelp` | boolean | false | Show help button |
| `showErrorTest` | boolean | false | Debug mode: show UI error toggle |
| `showInlineStatus` | boolean | true | Show status text |
| `closeLabel` | string | `"[x]"` | Close button text |
| `optionsLabel` | string | `"[options]"` | Options button text |
| `helpLabel` | string | `"[help]"` | Help button text |
| `idleLabel` | string | `"Idle"` | Default tool name |
| `statusText` | string | `""` | Status template (supports `${...}` variables) |
| `closeAction` | object | `{type:"close_screen"}` | Action on close button |
| `optionsAction` | object | `{type:"open_options"}` | Action on options button |
| `helpAction` | object | `{type:"open_modal",id:"help-modal"}` | Action on help button |
| `buttonBg` | color | `#142033` | Button background |
| `buttonHover` | color | `#1E3A5F` | Button hover background |
| `buttonDanger` | color | `#3A1010` | Close button background |
| `buttonDangerHover` | color | `#5A1818` | Close button hover |
| `buttonPaddingX` | integer | 5 | Horizontal button padding |
| `statusColor` | color | `#96A6C2` | Status text color |
| `textColor` | color | `#9CCBFF` | Button text color |
| `textHoverColor` | color | `#FFFFFF` | Button text hover color |
| `dangerTextColor` | color | `#AAAAAA` | Close button text color |
| `dangerTextHoverColor` | color | `#FF7777` | Close button text hover |
| `buttonOutline` | color | `#553A5A80` | Button outline color |
| `buttonHoverOutline` | color | `#4A86C8` | Button outline on hover |
| `buttonDangerOutline` | color | `#775A2020` | Close button outline |
| `buttonDangerHoverOutline` | color | `#AA4545` | Close button outline on hover |
| `outlineWidth` | integer | 1 | Outline thickness in pixels |
| `z` | integer | 100 | Z-order (stacking) |

**Template Variables (in `statusText`):**
- `${streaming}` - true/false
- `${historyCount}` - number of lines
- `${tool}` - current tool name
- `${detail}` - tool detail

---

### 4.2 history
**Purpose:** Display chat/interaction history with scrolling

**Render Method:** `renderHistory()`

**Required Properties:**
- `type`: `"history"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 220 | Default height (ignored if flex=true) |
| `flex` | boolean | false | If true, takes remaining space |
| `lineHeight` | integer | 11 | Line height in pixels |
| `showThoughts` | boolean | (from settings) | Show thought lines |
| `truncateCollapsed` | boolean | false | Truncate collapsed tool calls |
| `truncateIndicator` | boolean | false | Truncate streaming indicator |
| `scrollbarWidth` | integer | 3 | Scrollbar width |
| `background` | color | `#00000000` | Background (0x00 alpha = transparent) |
| `indicatorColor` | color | (tool color) | Streaming indicator color |
| `scrollTrack` | color | `#1A1A28` | Scrollbar track background |
| `scrollThumb` | color | `#4A4A6A` | Scrollbar thumb color |
| `scrollActive` | color | `#7070A0` | Scrollbar thumb hover color |

**Special Behavior:**
- If `flex: true`, widget consumes remaining vertical space after fixed-height widgets
- Can handle `history` type with `flex: true` - only ONE per layout
- Text wrapping respects panel width and scrollbar width

---

### 4.3 question_options
**Purpose:** Display clickable options for AI questions

**Render Method:** `renderQuestionOptions()`

**Required Properties:**
- `type`: `"question_options"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `buttonHeight` | integer | 16 | Height of each option button |
| `buttonGap` | integer | 2 | Gap between buttons |
| `action` | object | `{type:"send_message"}` | Action to trigger on selection |
| `buttonBg` | color | `#142033` | Button background |
| `buttonHover` | color | `#1E3A5F` | Button hover background |
| `textColor` | color | `#FFFFFF` | Button text color |
| `buttonOutline` | color | `#553A5A80` | Button outline |
| `buttonHoverOutline` | color | `#4A86C8` | Button outline hover |
| `outlineWidth` | integer | 1 | Outline thickness |
| `z` | integer | 100 | Z-order |

**Special Behavior:**
- Displays as `[A]`, `[B]`, `[C]` etc. based on `HudState.getQuestion().options()`
- If no pending question, renders nothing (height = 0)
- Option selection is passed as `optionValue` to action handler

---

### 4.4 input
**Purpose:** Text input field with multiline support

**Render Method:** `renderInput()`

**Required Properties:**
- `type`: `"input"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 72 | Maximum height (auto-expands with content) |
| `maxLength` | integer | 1000 | Maximum input length |
| `minRows` | integer | 1 | Minimum visible rows |
| `lineHeight` | integer | 11 | Line height in pixels |
| `prompt` | string | `"◆"` | Prompt character |
| `placeholder` | string | `"Type a message..."` | Placeholder text |
| `divider` | color | `#2A2A3A` | Divider line above input |
| `background` | color | `#00000000` | Background (transparent by default) |
| `promptColor` | color | (panel label) | Prompt text color |
| `placeholderColor` | color | `#555555` | Placeholder text color |
| `textColor` | color | (user color) | Input text color |
| `caretColor` | color | (textColor) | Cursor/caret color |
| `outline` | color | `#553A5A80` | Text box outline |
| `outlineWidth` | integer | 1 | Outline thickness |

**Special Behavior:**
- Height expands from `minRows * lineHeight + 10` up to `height` based on content
- Supports Shift+Enter for newlines, Enter to submit
- Ctrl+V for paste
- Arrow keys, Home/End, Backspace, Delete all work
- Wraps text internally but maintains cursor position
- Color setting keys can use syntax: `"$ui.input_text"` or `"setting:ui.input_text"`

---

### 4.5 text
**Purpose:** Static text display

**Render Method:** `renderText()`

**Required Properties:**
- `type`: `"text"`
- `text`: string (supports templates with `${...}`)

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 14 | Fixed height (or computed if wrap=true) |
| `text` | string | required | Text content (supports `${streaming}`, `${tool}`, etc.) |
| `color` | color | `#CCCCCC` | Text color |
| `center` | boolean | false | Center-align text |
| `wrap` | boolean | false | Enable text wrapping |

**Special Behavior:**
- If `wrap: true`, height is auto-computed based on word-wrap
- Splits on `\n` for paragraph breaks
- Each line is wrapped individually at maxWidth

---

### 4.6 action_row
**Purpose:** Row of action buttons

**Render Method:** `renderActionRow()`

**Required Properties:**
- `type`: `"action_row"`
- `buttons`: array of button objects

**Button Object Structure:**
```json
{
  "label": "Button Text",
  "action": { "type": "...", ... }
}
```

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 20 | Button height |
| `gap` | integer | 4 | Gap between buttons |
| `buttons` | array | required | Array of button objects |
| `buttonBg` | color | `#142033` | Button background |
| `buttonHover` | color | `#1E3A5F` | Button hover background |
| `color` | color | `#FFFFFF` | Button text color |
| `buttonOutline` | color | `#553A5A80` | Button outline |
| `buttonHoverOutline` | color | `#4A86C8` | Button outline hover |
| `outlineWidth` | integer | 1 | Outline thickness |
| `z` | integer | 100 | Z-order |

**Special Behavior:**
- Buttons are distributed evenly across available width
- Each button gets width: `(panelW - padding*2 - totalGaps) / buttonCount`
- Minimum button width is 24 pixels

---

### 4.7 dropdown
**Purpose:** Dropdown menu selector

**Render Method:** `renderDropdown()`

**Required Properties:**
- `type`: `"dropdown"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `id` | string | `"dropdown"` | Unique identifier for dropdown state |
| `label` | string | `"Select"` | Button label |
| `height` | integer | 18 | Button height |
| `options` | array | required | Array of option objects |
| `optionHeight` | integer | 16 | Height of each option |
| `buttonBg` | color | `#142033` | Button background |
| `buttonHover` | color | `#1E3A5F` | Button hover background |
| `color` | color | (panel label) | Text color |
| `buttonOutline` | color | `#553A5A80` | Button outline |
| `buttonHoverOutline` | color | `#4A86C8` | Button outline hover |
| `outlineWidth` | integer | 1 | Outline thickness |
| `z` | integer | 200 | Z-order (dropdowns are above base widgets) |

**Option Object Structure:**
```json
{
  "label": "Option Text",
  "action": { "type": "...", ... }
}
```

**Special Behavior:**
- Dropdown state is managed by `activeDropdownId` in SchemaScreen
- Button shows ` ▼` when closed, ` ▲` when open
- Options render below button when open
- Height calculation includes open options: `base + (options.count * optionHeight)`

---

### 4.8 setting_toggle
**Purpose:** Toggle a boolean setting

**Render Method:** `renderSettingToggle()`

**Required Properties:**
- `type`: `"setting_toggle"`
- `key`: string (setting key)

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 18 | Widget height |
| `key` | string | required | Settings key (e.g., `"ui.thoughts_visible"`) |
| `label` | string | (key) | Display label |
| `trueLabel` | string | `"On"` | Label when true |
| `falseLabel` | string | `"Off"` | Label when false |
| `trueValue` | string | `"true"` | Value to store for true |
| `falseValue` | string | `"false"` | Value to store for false |
| `color` | color | `#CCCCCC` | Label text color |
| `background` | color | `#142033` | Background color |
| `hover` | color | `#1E3A5F` | Hover background |
| `stateOnColor` | color | `#55FF55` | "On" state text color |
| `stateOffColor` | color | `#AAAAAA` | "Off" state text color |
| `outline` | color | `#553A5A80` | Outline color |
| `hoverOutline` | color | `#4A86C8` | Outline hover |
| `outlineWidth` | integer | 1 | Outline thickness |
| `z` | integer | 100 | Z-order |

**Special Behavior:**
- Left-aligned label, right-aligned state
- State is retrieved from `ModSettings.getBoolForPlugin(plugin, key)`
- Clicking sends `toggle_setting` action to server and updates local settings

---

### 4.9 state_badge
**Purpose:** Display current state (streaming, tool, etc.)

**Render Method:** `renderStateBadge()`

**Required Properties:**
- `type`: `"state_badge"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 14 | Widget height |
| `bind` | string | `"streaming"` | What to display: `"streaming"` or `"current_tool"` |
| `trueText` | string | `"Streaming"` | Text when streaming=true |
| `falseText` | string | `"Idle"` | Text when streaming=false |
| `emptyText` | string | `"Idle"` | Text when current_tool=null |
| `background` | color | `#142033` | Background |
| `color` | color | `#FFFFFF` | Text color |

**Special Behavior:**
- If `bind: "current_tool"`, displays `[toolname] detail`
- If `bind: "streaming"`, displays current streaming status
- Auto-sizing based on text width

---

### 4.10 tab_container
**Purpose:** Tabbed interface with multiple content areas

**Render Method:** `renderTabContainer()`

**Required Properties:**
- `type`: `"tab_container"`
- `tabs`: array of tab objects

**Tab Object Structure:**
```json
{
  "label": "Tab Name",
  "widgets": [ /* widgets for this tab */ ]
}
```

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 220 | Total container height |
| `id` | string | `"tabs"` | Unique identifier for tab state |
| `tabs` | array | required | Array of tab objects |
| `tabHeight` | integer | 18 | Height of tab buttons |
| `tabGap` | integer | 3 | Gap between tab buttons |
| `tabBg` | color | `#142033` | Inactive tab background |
| `tabActiveBg` | color | `#1E3A5F` | Active tab background |
| `tabColor` | color | `#CCCCCC` | Inactive tab text |
| `tabActiveColor` | color | `#FFFFFF` | Active tab text |
| `tabOutline` | color | `#553A5A80` | Inactive tab outline |
| `tabActiveOutline` | color | `#4A86C8` | Active tab outline |
| `outlineWidth` | integer | 1 | Outline thickness |
| `contentDivider` | color | `#2A2A3A` | Divider line below tabs |
| `z` | integer | 100 | Z-order |

**Special Behavior:**
- Tab state is managed by `selectedTabByContainer` map keyed by `id`
- Each tab can contain its own widget array
- Content height = `height - tabHeight - 3`
- Tabs are auto-sized based on label width + 12px padding

**Note:** In `StandardWidgets`, this type is registered as `"tabs"`. `SchemaScreen` also registers `"tab_container"`. Use `"tab_container"` in schema JSON for full rendering support.

---

### 4.11 divider
**Purpose:** Visual separator line

**Render Method:** Simple `ctx.fill()` call

**Required Properties:**
- `type`: `"divider"`

**Optional Properties:**

| Property | Type | Default |
|----------|------|---------|
| `color` | color | `#2A2A3A` |

**Special Behavior:**
- Height is always 1 pixel
- Full width of panel (minus padding)

---

### 4.12 spacer
**Purpose:** Empty vertical space

**Render Method:** No-op (just allocates space)

**Required Properties:**
- `type`: `"spacer"`

**Optional Properties:**

| Property | Type | Default |
|----------|------|---------|
| `height` | integer | 8 |

---

### 4.13 hint
**Purpose:** Small informational text

**Render Method:** `renderHint()`

**Required Properties:**
- `type`: `"hint"`

**Optional Properties:**

| Property | Type | Default |
|----------|------|---------|
| `text` | string | `""` |
| `color` | color | `#777788` |
| `height` | integer | 12 |

---

### 4.14 panel
**Purpose:** Nested sub-panel that fills remaining vertical space (flex)

**Render Method:** `renderPanel()`

**Required Properties:**
- `type`: `"panel"`

**Special Behavior:**
- Acts as a flex widget (takes remaining space), similar to `history` with `flex: true`
- Can contain its own `widgets` array rendered within the panel bounds

---

### 4.15 collapsible
**Purpose:** Collapsible section with a toggle header

**Render Method:** `renderCollapsible()`

**Required Properties:**
- `type`: `"collapsible"`
- `id`: string (state key)

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `id` | string | required | Identifies collapse state across renders |
| `label` | string | `""` | Header label |
| `defaultOpen` | boolean | false | Initial open state |
| `widgets` | array | `[]` | Child widgets shown when open |

**Special Behavior:**
- Open/closed state tracked by `collapsibleOpen` map keyed by `id`
- Toggle via `toggle_collapsible` internal action with matching `id`
- Child widgets are only rendered and measured when open

---

### 4.16 scroll_panel
**Purpose:** Scrollable container for a list of child widgets

**Render Method:** `renderScrollPanel()`

**Required Properties:**
- `type`: `"scroll_panel"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `height` | integer | 120 | Visible height of the panel |
| `id` | string | `"scroll"` | State key for scroll offset |
| `widgets` | array | `[]` | Child widgets |
| `scrollbarWidth` | integer | 3 | Scrollbar width |

**Special Behavior:**
- Scroll offset tracked by `scrollPanelOffsets` map keyed by `id`
- Mouse wheel events inside the region adjust the offset
- Children are clipped to the visible area

---

### 4.17 search_box
**Purpose:** Text input for filtering content (query field)

**Render Method:** `renderSearchBox()`

**Required Properties:**
- `type`: `"search_box"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `id` | string | `"search"` | State key for text and cursor |
| `placeholder` | string | `"Search..."` | Placeholder text |
| `height` | integer | 18 | Widget height |

**Special Behavior:**
- Text state stored in `textFieldValues` keyed by `id`
- Supports `toggle_query_token` action to append/remove filter tokens
- Paired with `filter_chips` and `multi_select_dropdown` for faceted search

---

### 4.18 filter_chips
**Purpose:** Row of toggleable filter tag chips

**Render Method:** `renderFilterChips()`

**Required Properties:**
- `type`: `"filter_chips"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `stateId` | string | `"search"` | FilterState key shared with search_box |
| `tags` | array | `[]` | Array of tag strings to show as chips |
| `height` | integer | 20 | Widget height |

**Special Behavior:**
- Selected tags are stored in `FilterState.selectedTags` keyed by `stateId`
- Clicking a chip toggles its presence in the selected set

---

### 4.19 multi_select_dropdown
**Purpose:** Dropdown for selecting multiple items from a list

**Render Method:** `renderMultiSelectDropdown()`

**Required Properties:**
- `type`: `"multi_select_dropdown"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `stateId` | string | `"search"` | FilterState key shared with search_box |
| `label` | string | `"Select"` | Button label when closed |
| `options` | array | `[]` | Array of option strings |
| `height` | integer | 18 | Button height |
| `optionHeight` | integer | 16 | Height of each option row |

**Special Behavior:**
- Selected items stored in `FilterState.selectedItems`
- Dropdown state tracked by `activeDropdownId`

---

### 4.20 modal
**Purpose:** Modal dialog overlay rendered above all other widgets

**Render Method:** `renderModal()` (called separately after normal widget pass)

**Required Properties:**
- `type`: `"modal"`

**Optional Properties:**

| Property | Type | Default | Notes |
|----------|------|---------|-------|
| `id` | string | `"default-modal"` | Modal identifier |
| `title` | string | `"Modal"` | Title text |
| `height` | integer | 240 | Modal height |
| `maxWidth` | integer | 460 | Maximum width |
| `widthPercent` | float | 0.72 | Width as % of screen |
| `padding` | integer | 8 | Internal padding |
| `titleHeight` | integer | 16 | Title bar height |
| `closeLabel` | string | `"[x]"` | Close button text |
| `widgets` | array | required | Modal content widgets |
| `closeOnBackdrop` | boolean | true | Close when clicking outside |
| `background` | color | `#0A0A0F` | Modal background |
| `titleBackground` | color | `#111118` | Title background |
| `divider` | color | `#2A2A3A` | Title divider |
| `closeButtonBg` | color | `#142033` | Close button background |
| `closeButtonHover` | color | `#3A1010` | Close button hover |
| `closeColor` | color | `#AAAAAA` | Close button text |
| `closeHoverColor` | color | `#FF7777` | Close button text hover |
| `closeOutline` | color | `#553A5A80` | Close button outline |
| `closeHoverOutline` | color | `#AA4545` | Close button outline hover |
| `closeOutlineWidth` | integer | 1 | Close button outline width |
| `backdrop` | color | `#AA000000` | Semi-transparent backdrop |
| `backdropZ` | integer | 900 | Backdrop z-order |
| `z` | integer | 1000 | Modal z-order |

**Special Behavior:**
- Modals are rendered AFTER all normal widgets
- Modal state is tracked by `activeModalId` in SchemaScreen
- Only ONE modal can be open at a time
- Modal children are rendered via `renderWidgetsInRegion()`
- `open` property in schema is ignored; server uses action to set `activeModalId`

---

## 5. Action System

All clickable elements trigger actions when clicked. Actions are JsonObjects with required and optional properties.

### 5.1 Standard Action Properties

```json
{
  "type": "action_type",
  "plugin": "target_plugin",
  "z": 100
}
```

| Property | Type | Purpose |
|----------|------|---------|
| `type` | string | **Required.** Action type identifier |
| `plugin` | string | Target plugin (defaults to current screen's plugin) |
| `z` | integer | Z-order override for click priority |

### 5.2 Internal Actions

Internal actions are built-in and do NOT require server round-trips:

| Type | Properties | Effect |
|------|-----------|--------|
| `close_screen` | — | Close the entire schema screen |
| `open_options` | — | Open settings/options screen |
| `open_help` | `id` | Open help modal (default: `"help-modal"`) |
| `open_modal` | `id` (required) | Open modal by ID |
| `close_modal` | — | Close active modal |
| `toggle_dropdown` | `id` (required) | Toggle dropdown open/closed |
| `set_tab` | `container` (required), `tabIndex` (required) | Switch active tab |
| `toggle_collapsible` | `id` (required), `defaultOpen` (optional) | Toggle collapsible section open/closed |
| `set_text_focus` | `id` (required) | Focus a text field by ID |
| `clear_text_state` | `id` (required) | Clear a text field and focus it |
| `toggle_query_token` | `stateId` (required), `token` (required) | Toggle a filter token in a search/filter state |
| `clear_history` | — | Clear history on client + server |
| `refresh_schema` / `request_history` | — | Request fresh schema from server |
| `send_message` | `text` (optional) | Send text to server as user input |
| `append_input` | `text` (required) | Append text to input field |
| `replace_input` | `text` (required) | Replace entire input |
| `submit_input` | — | Submit current input to server |
| `clear_input` | — | Clear input field |
| `set_setting` | `key`, `value` | Set a mod setting locally + server |
| `toggle_setting` | `key`, `trueValue`, `falseValue` | Toggle boolean setting |
| `toggle_schema_error` | — | Toggle debug error view (debug only) |

**Usage:**
```json
{
  "type": "send_message",
  "text": "Hello ${option}"
}
```

### 5.3 Server-Sent Actions

Custom actions sent by plugins are forwarded to the server via `ModPackets.sendInput()`.

**Supported Properties:**
- `type`: Custom action type
- `plugin`: Target plugin
- Any other custom properties

### 5.4 Template Variables in Action Text

Some actions support template substitution:

| Variable | Resolves To | Used In |
|----------|-------------|---------|
| `${option}` | Selected option value | `question_options`, dropdowns |
| `${streaming}` | "true" or "false" | `text` widget |
| `${tool}` | Current tool name | `text` widget, toolbar status |
| `${detail}` | Tool detail string | `text` widget |
| `${historyCount}` | Number of history lines | `text` widget, toolbar status |
| `${shortcut.*}` | Keybind name | `text` widget |

---

## 6. Global Configuration Structure

The `config` object in the schema defines global settings:

```json
{
  "config": {
    "keybinds": [ /* array of keybind definitions */ ],
    "colors": {
      "schemes": [ /* color scheme definitions */ ],
      "roles": [ /* color role definitions */ ],
      "palette": { "rows": 6, "cols": 21 }
    },
    "settings": {
      "tabs": [ /* settings UI tabs */ ]
    },
    "help": { /* help configuration */ }
  }
}
```

### 6.1 Keybinds Configuration

```json
{
  "keybinds": [
    {
      "id": "open_menu",
      "label": "Open Menu",
      "defaultKey": "P",
      "defaultMods": 0,
      "useMods": false
    }
  ]
}
```

**KeybindDef Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `id` | string | Keybind identifier |
| `label` | string | Display name |
| `defaultKey` | string | Key name (e.g., `"P"`, `"F3"`, `"ENTER"`) |
| `defaultMods` | integer | Bitmask: 0x1=Shift, 0x2=Ctrl, 0x4=Alt |
| `useMods` | boolean | Whether this keybind can use modifiers |

### 6.2 Colors Configuration

```json
{
  "colors": {
    "schemes": [
      {
        "name": "dark",
        "label": "Dark Theme",
        "user": "55FF55",
        "claude": "55FFFF",
        "tool": "FFAA00",
        "output": "888888",
        "system": "AAAAAA",
        "question": "FFFF55"
      }
    ],
    "roles": [
      { "key": "ui.input_text", "label": "Input Text Color" }
    ],
    "palette": { "rows": 6, "cols": 21 }
  }
}
```

**ColorSchemeDef:** Multiple color schemes can be defined. Each has role colors for user, claude, tool, output, system, question.

**ColorRoleDef:** Named color roles that can be referenced in widget color properties.

### 6.3 Settings Configuration

```json
{
  "settings": {
    "tabs": [
      {
        "name": "ui",
        "label": "UI Settings",
        "rows": [
          {
            "key": "ui.thoughts_visible",
            "label": "Show Thinking",
            "type": "toggle",
            "default": "false"
          }
        ]
      }
    ]
  }
}
```

Settings define the options available in the settings screen.

---

## 7. Overlay System (HUD)

Overlays are rendered as HUD elements in the game world (not in the modal UI):

```json
{
  "overlays": [
    {
      "id": "armor-bar",
      "plugin": "vibecraft",
      "type": "armor_slots",
      "position": { "x": 10, "y": 10, "anchor": "top-left" },
      "size": { "width": 100, "height": 20 },
      "style": { "background": "#0A0A0A", "border": "#FFFFFF" },
      "dataBinding": "armor.slots"
    }
  ]
}
```

**Overlay Types (registered in StandardWidgets.registerAll()):**
- `armor_slots` / `slots` → SlotOverlayWidget: draws an item from `ItemStack` binding data in a hotbar-style slot
- `bar` → BarOverlayWidget: fills a rectangle based on a 0–1 or 0–100 numeric binding; reads `background`/`foreground` colors from config
- `icon` → IconOverlayWidget: draws a text/character icon; uses binding data string if present, otherwise falls back to config `"icon"` field
- `text` → TextOverlayWidget: draws a string from binding data or config `"text"` field
- `hostile_indicator` → HostileIndicatorWidget: pulsing screen-edge directional arrow; binding must be `{"side":"left"|"right","timestamp":<epochMs>}`; fades after 3 seconds

**Position:** `x` and `y` are absolute pixel coordinates in the current implementation.
The `anchor` field in `OverlayDef` is parsed but not yet applied to offset calculations.
Overlay widgets render only when the matching plugin's screen is active (or plugin is blank/null).

---

## 8. Network Protocol

### 8.1 Server → Client (VibeCraftEventPayload)

Channel: `vibecraft:events`

The payload is raw UTF-8 JSON bytes. `PROTOCOL_VERSION` is a client-side constant (currently `1`); the client logs a warning if the server's reported version differs. Each message is a JSON object with a required `"type"` field.

**Handled Event Types (ModPackets.EVENT_HANDLERS):**

| Type | Key Fields | Effect |
|------|-----------|--------|
| `user_message` | `text`, `plugin` | Adds a user-colored line to HUD history |
| `stream_start` | `plugin` | Marks streaming active, clears tool/question state |
| `thinking` | `text` | Adds thought lines to HUD history |
| `question` | `prompt`, `options`, `plugin` | Adds question + option lines; sets pending question |
| `tool_call` | `tool`, `detail`, `plugin` | Sets current tool; adds collapsed tool line |
| `claude_text` | `lines`, `plugin` | Adds AI response lines with markdown rendering |
| `bash_output` | `lines`, `plugin` | Adds output-colored lines |
| `stream_end` | — | Clears streaming state; shows completion toast |
| `ui_schema` | `schema` (object) | Replaces/merges schema in UiSchemaStore; triggers reload |
| `ui_schema_patch` | `patch` (object) | Deep-merges patch into current schema; triggers reload |
| `binding_update` | `binding` (string), `value` | Updates a single key in OverlayDataBinding |
| `binding_updates` | `values` (object) | Batch updates multiple keys in OverlayDataBinding |
| `open_screen` | `screenId` | Opens SchemaScreen for the named screen; duplicate-open guard (500 ms) |
| `close_screen` | — | Closes current screen |
| `history_chunk` | `entries`, `done`, `plugin`, optional `seq` | Buffered history delivery; replays on `done: true` |
| `history` | `entries`, `plugin`, optional `seq` | Replaces HUD history in one message |
| `settings` | `values` (object), `plugin`, optional `seq` | Applies server-sent settings to ModSettings |
| `ef_highlight_entities` | `hostile` (int array), `neutral` (int array) | Updates EntityHighlightManager with entity IDs to glow |

**Important field names:**
- `binding_update` uses field `"binding"` (not `"key"`) for the binding key name.
- `binding_updates` uses field `"values"` (not `"updates"`) for the key-value map.

**Sequence guards:** `history` and `settings` events may carry an optional `seq`/`sequence`/`revision`/`version` field. Out-of-order events (seq ≤ last seen for that plugin+channel) are silently dropped.

**Multi-plugin schema:** `ui_schema` merges by plugin — existing screens from other plugins are preserved. Screens from the same plugin(s) as the incoming schema are replaced.

### 8.2 Client → Server (VibeCraftInputPayload)

Channel: `vibecraft:input`

All messages are JSON with `type`, `plugin`, and `namespace` fields. Sent by `ModPackets` static helpers.

| Method | Type field | Other fields |
|--------|-----------|--------------|
| `sendInput(text, plugin)` | `"message"` | `message` |
| `requestHistory(plugin)` | `"request_history"` | — |
| `sendSetSetting(plugin, key, value)` | `"set_setting"` | `key`, `value` |
| `clearHistory(plugin)` | `"clear_history"` | — |

Custom server-side action types (from button clicks) are forwarded via `sendInput` as the action JSON string.

---

## 9. Widget Height Calculations

SchemaScreen calculates each widget's vertical space before rendering. Approximate defaults:

| Widget type | Default height | Notes |
|-------------|---------------|-------|
| `toolbar` | 14 | Fixed |
| `history` | 220 | Overridden when `flex: true` |
| `question_options` | dynamic | `options.count * (buttonH + gap) + 4`; 0 if no question |
| `input` | dynamic | Expands from `minRows * lineHeight + 10` up to `height` |
| `hint` | 12 | Fixed |
| `text` | 14 | Auto-computed if `wrap: true` |
| `action_row` | 20 | Fixed |
| `dropdown` | 18 | Adds `options.count * optionHeight` when open |
| `setting_toggle` | 18 | Fixed |
| `state_badge` | 14 | Fixed |
| `tab_container` | 220 | Fixed total; content area = height - tabHeight - 3 |
| `modal` | 0 | Rendered in a separate pass after normal widgets |
| `divider` | 1 | Fixed |
| `spacer` | 8 | Fixed |
| `panel` | flex | Takes remaining vertical space |
| `collapsible` | header + children | Children height added only when open |
| `scroll_panel` | configurable | Clips children to `height`, scrolls internally |
| `search_box` | 18 | Fixed |
| `filter_chips` | 20 | Fixed |
| `multi_select_dropdown` | 18 | Fixed closed; expands when open |

**Flex Layout:**
1. Calculate fixed heights for all non-flex, non-modal widgets
2. Count flex widgets (`history` with `flex: true`, or `panel`)
3. Remaining space = `max(120, screenHeight - titleHeight - fixedHeight - padding)`
4. Flex height per widget = `remaining / flexCount`
5. Only one flex widget per layout is fully supported

---

## 10. Color Format and Resolution

Color properties accept ARGB hex strings:

**Formats:**
- `#RRGGBB` → Converted to `0xFFRRGGBB` (full opacity)
- `#AARRGGBB` → Parsed as `0xAARRGGBB` (with alpha)

**Color Resolution (in colorVal()):**
1. Check for `{key}Setting` property → look up in `ModSettings`
2. Check if value starts with `"setting:"` → look up in `ModSettings`
3. Check if value starts with `"$"` → look up in `ModSettings`
4. Parse as literal hex color
5. Use fallback if parsing fails

**Example:**
```json
{
  "textColor": "#FFFFFF",
  "textColorSetting": "ui.custom_text",
  "promptColor": "setting:ui.prompt",
  "caretColor": "$ui.caret"
}
```

---

## 11. Z-Order (Layering)

Z-order controls which elements appear on top when overlapping:

**Base Z-Orders (in SchemaScreen):**
- `Z_BASE = 100` - Normal widgets (toolbar, text, buttons)
- `Z_DROPDOWN = 200` - Dropdown menus
- `Z_MODAL_BACKDROP = 900` - Semi-transparent backdrop behind modals
- `Z_MODAL = 1000` - Modal dialog itself

**Higher Z = rendered on top**

Widgets can override Z via the `z` property in the action object or widget itself.

---

## 12. Validation and Error Handling

**What VALIDATES in the code:**
- ✅ `ScreenDef.fromJson()` validates required fields (id, plugin, title)
- ✅ `SchemaConfig.reload()` catches exceptions parsing individual screens
- ✅ Helper methods like `strVal()`, `intVal()`, `colorVal()` have safe fallbacks
- ✅ Widget height calculations handle null/missing values with defaults
- ✅ Action type dispatch uses containsKey() before running handlers

**What DOES NOT validate:**
- ❌ Widget property value ranges (e.g., negative heights)
- ❌ Color hex format (parseColor() returns fallback on error)
- ❌ Array bounds (empty arrays render as nothing, not error)
- ❌ Circular references or missing widget types (skipped silently)
- ❌ Action properties - anything can be passed to the action handler

**Error Handling Strategy:**
- Parse errors in optional fields → use defaults
- Missing required fields → skip that item (screen, widget)
- Invalid action type → log and ignore (DebugConfig.DEBUG_ACTIONS)
- Unknown widget type → no-op render, no error shown

---

## 13. Summary: What's Actually Supported

### Safe to Use:
✅ All 20 widget types listed in section 4  
✅ All internal action types listed in section 5.2  
✅ Color properties with hex values or settings references  
✅ Template variables in text and action fields  
✅ Nested widgets in tabs, modals, collapsibles, scroll panels, panels  
✅ Flex layout with `history` widget or `panel` widget  
✅ Keyboard input, text wrapping, scrolling  

### Validated Properties:
✅ All properties explicitly extracted with `strVal()`, `intVal()`, `boolVal()`, `colorVal()`  
✅ All documented default values  
✅ Property names must match exactly (case-sensitive)  

### Common Mistakes to Avoid:
❌ Typos in property names (silently ignored)  
❌ Mixing content types (e.g., string where object expected)  
❌ Multiple flex widgets in one layout (only first counts)  
❌ Missing required action type field  
❌ Invalid color hex (falls back to default)  
❌ Negative dimensions (may render incorrectly)  
❌ Modal outside screens array (never shown)  
❌ Using `"key"` instead of `"binding"` in `binding_update` events (field is `"binding"`)  
❌ Using `"updates"` instead of `"values"` in `binding_updates` events (field is `"values"`)  

---

## 14. Example: Complete Valid Schema

```json
{
  "defaultPlugin": "enchantforge",
  "title": "Enchant Forge",
  "panel": {
    "maxWidth": 600,
    "widthPercent": 0.75,
    "padding": 10,
    "titleHeight": 16,
    "background": "#0A0A0F",
    "label": "#CCCCCC"
  },
  "screens": [
    {
      "id": "enchantforge:editor",
      "plugin": "enchantforge",
      "title": "Enchantment Editor",
      "priority": 100,
      "widgets": [
        {
          "type": "toolbar",
          "showClose": true,
          "showHelp": true,
          "statusText": "Editing: ${tool}"
        },
        {
          "type": "text",
          "text": "Available Enchantments",
          "color": "#FFFF55"
        },
        {
          "type": "history",
          "flex": true,
          "lineHeight": 11
        },
        {
          "type": "action_row",
          "height": 20,
          "buttons": [
            {
              "label": "Save",
              "action": { "type": "save_enchant" }
            },
            {
              "label": "Cancel",
              "action": { "type": "close_screen" }
            }
          ]
        },
        {
          "type": "input",
          "height": 60,
          "minRows": 2,
          "placeholder": "Enter enchant name..."
        }
      ]
    }
  ]
}
```

---

**End of Specification**
