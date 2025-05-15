/**
 * Enumerates types of cyto events.
 *
 * Extracted from: https://js.cytoscape.org/#events
 */
export enum cytoEvent {
  /**
   * # User input device events.
   */

  /**
   * When the mouse button is pressed.
   */
  MOUSE_DOWN = "mousedown",
  /**
   * When the mouse button is released.
   */
  MOUSE_UP = "mouseup",
  /**
   * After `mousedown` then `mouseup`.
   */
  CLICK = "click",
  /**
   * When the cursor is put on top of the target.
   */
  MOUSE_OVER = "mouseover",
  /**
   * When the cursor is moved off of the target.
   */
  MOUSE_OUT = "mouseout",
  /**
   * When the cursor is moved somewhere on top of the target.
   */
  MOUSE_MOVE = "mousemove",
  /**
   * When one or more fingers starts to touch the screen.
   */
  TOUCH_START = "touchstart",
  /**
   * When one or more fingers are moved on the screen.
   */
  TOUCH_MOVE = "touchmove",
  /**
   * When one or more fingers are removed from the screen.
   */
  TOUCH_END = "touchend",

  /**
   * When the viewport is resized (usually by calling cy.resize(),
   * a window resize, or toggling a class on the Cytoscape.js div).
   */
  RESIZE = "resize",

  /**
   * # High level events.
   */

  /**
   * Tap start event (either mousedown or touchstart).
   */
  TAP_START = "tapstart",
  /**
   * Move event (either touchmove or mousemove).
   */
  TAP_DRAG = "tapdrag",
  /**
   * Over element event (either touchmove or mousemove/mouseover).
   */
  TAP_DRAG_OVER = "tapdragover",
  /**
   * Off of element event (either touchmove or mousemove/mouseout).
   */
  TAP_DRAGOUT = "tapdragout",
  /**
   * Tap end event (either mouseup or touchend).
   */
  TAP_END = "tapend",
  /**
   * Tap event (either click, or touchstart followed by touchend without touchmove).
   */
  TAP = "tap",
  /**
   * Tap hold event.
   */
  TAP_HOLD = "taphold",
  /**
   * Right-click mousedown or two-finger tapstart.
   */
  CXT_TAP_START = "cxttapstart",
  /**
   * Right-click mouseup or two-finger tapend.
   */
  CXT_TAP_END = "cxttapend",
  /**
   * Right-click or two-finger tap.
   */
  CXT_TAP = "cxttap",
  /**
   * Mousemove or two-finger drag after cxttapstart but before cxttapend.
   */
  CXT_DRAG = "cxtdrag",
  /**
   * When going over a node via cxtdrag.
   */
  CXT_DRAG_OVER = "cxtdragover",
  /**
   * When going off a node via cxtdrag.
   */
  CXT_DRAG_OUT = "cxtdragout",
  /**
   * When starting box selection.
   */
  BOX_START = "boxstart",
  /**
   * When ending box selection.
   */
  BOX_END = "boxend",
  /**
   * Triggered on elements when selected by box selection.
   */
  BOX_SELECT = "boxselect",
  /**
   * Triggered on elements when inside the box on boxend.
   */
  BOX = "box",

  /**
   * # Custom cytoscape events.
   */

  /**
   * When an element is added to the graph.
   */
  ADD = "add",
  /**
   * When an element is removed from the graph.
   */
  REMOVE = "remove",
  /**
   * When an element is moved w.r.t. topology.
   */
  MOVE = "move",
  /**
   * When the compound parent is changed.
   */
  NODES = "nodes",
  /**
   * When the source or target is changed.
   */
  EDGES = "edges",
  /**
   * When an element is selected.
   */
  SELECT = "select",
  /**
   * When an element is unselected.
   */
  UNSELECT = "unselect",
  /**
   * When an element is selected by a tap gesture.
   */
  TAP_SELECT = "tapselect",
  /**
   * When an element is unselected by a tap elsewhere.
   */
  TAP_UNSELECT = "tapunselect",
  /**
   * When an element is locked.
   */
  LOCK = "lock",
  /**
   * When an element is unlocked.
   */
  UNLOCK = "unlock",
  /**
   * When an element is grabbed directly (including only the one node directly under the cursor or the user’s finger).
   */
  GRAB_ON = "grabon",
  /**
   * When an element is grabbed (including all elements that would be dragged).
   */
  GRAB = "grab",
  /**
   * When an element is grabbed and then moved.
   */
  DRAG = "drag",
  /**
   * When an element is freed (i.e. let go from being grabbed).
   */
  FREE = "free",
  /**
   * When an element is freed directly (including only the one node directly under the cursor or the user’s finger).
   */
  FREE_ON = "freeon",
  /**
   * When an element is freed after being dragged (i.e. grab then drag then free).
   */
  DRAG_FREE = "dragfree",
  /**
   * When an element is freed after being dragged directly (i.e. grabon, drag, freeon).
   */
  DRAG_FREE_ON = "dragfreeon",
  /**
   * When an element changes position.
   */
  POSITION = "position",
  /**
   * When an element’s data is changed.
   */
  DATA = "data",
  /**
   * When an element’s scratchpad data is changed.
   */
  SCRATCH = "scratch",
  /**
   * When an element’s style is changed.
   */
  STYLE = "style",
  /**
   * When a node’s background image is loaded.
   */
  BACKGROUND = "background",

  /**
   * # Edge Handle Events.
   */
  EH_COMPLETE = "ehcomplete",
}
