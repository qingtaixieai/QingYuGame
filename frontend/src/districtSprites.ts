// District sprites use one eight-pixel module for façades and one four-pixel module for furnishings.
export const townPixels = {
  "palette": {
    "R": "#c9574a",
    "r": "#8f3a33",
    "W": "#f2e9d8",
    "w": "#c9b9a0",
    "D": "#4a3a30",
    "G": "#79a86a",
    "g": "#4d7049",
    "Y": "#b5895a",
    "y": "#7a5738",
    "B": "#8496a3",
    "b": "#5a6b78",
    "o": "#e08a3c",
    "L": "#ffe09b",
    "s": "#aa9876",
    "H": "#df8770"
  },
  "inn": [
    "....HHHH....",
    "...HRRRRH...",
    "..HRRRRRRH..",
    ".HRRRRRRRRH.",
    "rrrrrrrrrrrr",
    ".wWwWWwWwWw.",
    ".wWLWWWWLWw.",
    ".wWWrrrrWWw.",
    ".wWLrDDrLWw.",
    ".wwWrDDrWww.",
    "..ssDDDDss.."
  ],
  "shop": [
    "............",
    "..gggggggg..",
    "..gGGGGGGg..",
    ".gGGGGGGGGg.",
    ".gWGWGWGWGg.",
    "gWGwGWGWGWGg",
    ".sLLWwwLLWs.",
    ".sLDWDWDLWs.",
    ".swwWDDWwws.",
    ".ssssDDssss."
  ],
  "workshop": [
    "..YYYY......",
    ".YYYYYY.....",
    ".YYYYYYYYYY.",
    "yyyyyyyyyyyy",
    ".yWWWWWWWWy.",
    ".yWYYWyYWwy.",
    ".yWWWWWWwwy.",
    ".yWyyDDyyWy.",
    ".yWyyDDyyWy.",
    ".yyyyDDyyyy."
  ],
  "smith": [
    "........bb..",
    "........bB..",
    "..BBBBBBbB..",
    ".BBBBBBBbB..",
    "bbbbbbbbbbb.",
    ".sBsBsBsBsB.",
    ".BsBDoDBsBB.",
    ".sBsLoLsBBs.",
    ".BsBDDDsBBB.",
    ".sBBsDDsBsB."
  ]
} as const;

export const innPixels = {
  "palette": {
    "a": "#C89A62",
    "b": "#A8763F",
    "c": "#7A4E28",
    "d": "#EFDCB4",
    "e": "#F7EBD5",
    "f": "#F5C86B",
    "g": "#4A3B2E",
    "h": "#B4553F"
  },
  "counter": [
    "............",
    "..fff...ggee",
    "..ggg...eeee",
    "aaaaaaaaaaaa",
    "abbbbbbbbbba",
    "abbbbbbbbbba",
    "abbbbbbbbbba",
    "cccccccccccc"
  ],
  "bed": [
    "cccccccccccc",
    "abbbbbbbbbba",
    "abddddddddba",
    "abhhhhhhhhba",
    "abhhhhhhhhba",
    "abddddddddba",
    "abhhhhhhhhba",
    "acccccccccca"
  ],
  "shelf": [
    "bbbbbbbbbbbb",
    "bff.hh.ee.gb",
    "bff.hh.ee.gb",
    "bbbbbbbbbbbb",
    "b.e.gg.ff..b",
    "b.e.gg.ff..b",
    "bbbbbbbbbbbb",
    "acccccccccca"
  ],
  "bench": [
    "............",
    "cccccccccccc",
    "caahhhhhaabc",
    "caahhhhhaabc",
    "caaaaaaaabcc",
    "cbbbbbbbbbbc",
    "cc........cc"
  ],
  "floor": [
    "#CBB18A",
    "#C0AA83",
    "#D5B688"
  ],
  "wall": [
    "#EADFC4",
    "#D6C6A4",
    "#B79F79"
  ]
} as const;
