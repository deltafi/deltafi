const cxtMenuDefaults: any = {
  menuRadius: function (ele: any) {
    return 50;
  }, // the outer radius (node center to the end of the menu) in pixels. It is added to the rendered size of the node. Can either be a number or function as in the example.
  selector: "node", // elements matching this Cytoscape.js selector will trigger cxtmenus
  commands: [
    {
      content: "Test Mode",
      select: function (ele: any) {
        console.log(ele.id());
      },
    },

    {
      content: "start/stop/reset",
      // content: '<button style="font-size:24px">Button <i class="fa fa-flash"></i></button>',
      select: function (ele: any) {
        console.log(ele.data("name"));
      },
      enabled: true,
    },

    {
      content: "View info",
      select: function (ele: any) {
        console.log(ele.position());
      },
    },
  ], // function( ele ){ return [ /*...*/ ] }, // a function that returns commands or a promise of commands
  fillColor: "black", // the background colour of the menu
  activeFillColor: "grey", // the colour used to indicate the selected command
  activePadding: 8, // additional size in pixels for the active command
  indicatorSize: 24, // the size in pixels of the pointer to the active command, will default to the node size if the node size is smaller than the indicator size,
  separatorWidth: 3, // the empty spacing in pixels between successive commands
  spotlightPadding: 8, // extra spacing in pixels between the element and the spotlight
  adaptativeNodeSpotlightRadius: true, // specify whether the spotlight radius should adapt to the node size
  //minSpotlightRadius: 24, // the minimum radius in pixels of the spotlight (ignored for the node if adaptativeNodeSpotlightRadius is enabled but still used for the edge & background)
  //maxSpotlightRadius: 38, // the maximum radius in pixels of the spotlight (ignored for the node if adaptativeNodeSpotlightRadius is enabled but still used for the edge & background)
  openMenuEvents: "cxttap", // space-separated cytoscape events that will open the menu; only `cxttapstart` and/or `taphold` work here
  itemColor: "white", // the colour of text in the command's content
  itemTextShadowColor: "transparent", // the text shadow colour of the command's content
  zIndex: 9999, // the z-index of the ui div
  atMouse: false, // draw menu at mouse position
  outsideMenuCancel: 8, // if set to a number, this will cancel the command if the pointer is released outside of the spotlight, padded by the number given
};

export { cxtMenuDefaults };
