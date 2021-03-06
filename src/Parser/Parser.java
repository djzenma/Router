/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Parser;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Parser {
    static public final String DEF_EXT = ".def", LEF_EXT = ".lef" ;
    private String defFile, lefFile;
    private final String DIEAREA_REGEX = "DIEAREA.+" ;
    private final String SECTION_REGEX = "\\s+.+\\n(.+\\n)+";
    private final String  SITE_REGEX = "SITE\\s+core\\n(.+\\n)+END" ;
    private final String  MACRO_REGEX = "MACRO.+(\\n.+)+";
    static public final String PINS = "PINS", COMPONENTS = "COMPONENTS", NETS = "NETS", SPECNETS = "SPECIALNETS";

    public String absolutePath;


    public Parser() {
        Path path = Paths.get(".");        // Gets the project's absolute path
        absolutePath = path.toAbsolutePath().toString();
        absolutePath = absolutePath.substring(0, absolutePath.length() -1) + "/src";
        readFile(absolutePath + "/Parser/Resources/osu035.lef", Parser.LEF_EXT);
        readFile(absolutePath + "/Parser/Resources/arbiter_unroute.def", Parser.DEF_EXT);
    }

    public String readFile(String name , String ext) {
        String line;
        StringBuilder file = new StringBuilder();
        // FileReader reads text files in the default encoding.
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(name);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Parser.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Always wrap FileReader in BufferedReader.
        assert fileReader != null;
        BufferedReader bufferedReader =
            new BufferedReader(fileReader);

        try {
            while((line = bufferedReader.readLine()) != null) {
                file.append('\n');
                file.append(line);
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ext.equals(DEF_EXT))
            this.defFile = file.toString();
        else
            this.lefFile = file.toString();

        return file.toString();
    }

    public List<String> getSection(String section , String ext) {
        String file;
        if (ext.equals(DEF_EXT))
            file = this.defFile;
        else
            file = this.lefFile;

        return regexMatcher(section, file);
    }

    public Hashtable<String, Layer> getLayersTable() {
        String metalDirection, name;
        float num;

        Hashtable<String, Layer> layersTable = new Hashtable<>();

        List<String> metalLayersBlocks = regexMatcher("LAYER.+\\n\\s+TYPE\\s+ROUTING.+\\n\\s+DIRECTION.+", this.lefFile);
        for (String layerBlock : metalLayersBlocks) {
            name = regexMatcher("LAYER.+", layerBlock).get(0).replaceAll("(LAYER|\\s+|;)", "");
            metalDirection = regexMatcher("DIRECTION.+", layerBlock).get(0).replaceAll("(DIRECTION|\\s+|;)", "");
            num = Float.parseFloat(regexMatcher("\\d+", layerBlock).get(0));

            layersTable.put(name,new Layer(name, metalDirection, num));
        }

        List<String> viaLayersBlocks = regexMatcher("LAYER.+\\n\\s+TYPE\\s+CUT.+", this.lefFile);
        for (String layerBlock : viaLayersBlocks) {
            name = regexMatcher("LAYER.+", layerBlock).get(0).replaceAll("(LAYER|\\s+|;)", "");
            if(!regexMatcher("\\d+", layerBlock).isEmpty()) {
                num = Float.parseFloat(regexMatcher("\\d+", layerBlock).get(0));
                layersTable.put(name,new Layer(name, num));
            }
            else
                layersTable.put(name,new Layer(name));

        }

        return layersTable;
    }

    /*
     *  @return Hash Table with all the MACROS placed from the DEF File
     */
    public Hashtable<String, Macro> getPlacedMacros() {
       Hashtable < String , Macro> macrosPlaced = new Hashtable<>();

       List<String> matches = this.getSection(COMPONENTS+SECTION_REGEX, Parser.DEF_EXT);

       String match = matches.get(0);
       String [] comps = match.split("\n");

       for(String component : comps) {
            String[] spaceDelimited = component.split("\\s");
            if (spaceDelimited.length == 11)
                macrosPlaced.put(spaceDelimited[1], new Macro(spaceDelimited[2], new Vector(Integer.parseInt(spaceDelimited[6]), Integer.parseInt(spaceDelimited[7]))));
       }

        macrosPlaced.put("PIN", new Macro("PIN", new Vector(0,0,0)));
/*
       regexMatcher( "-.*\\n+\\s*\\+\\s+LAYER.+\\n\\s+\\+\\s+PLACED.*;", regexMatcher("PINS\\s+\\d+(.*\\n)+END\\s+PINS", this.defFile).get(0)).forEach((pinBlock) -> {
           String[] delimited = pinBlock.split("\\s");
           macrosPlaced.put(delimited[1], new Macro("PIN", new Vector(Double.parseDouble(delimited[23]), Double.parseDouble(delimited[24]))));    // Base location
       });
*/
       return macrosPlaced ;
    }

    /*
     *  @return Hashtable with all the Library MACROS defined in the LEF File
     */
    public Hashtable<String, Macro> getMacrosDefinitions(Hashtable<String, Layer> layersSet) {
        Hashtable <String, Macro> macrosDefinitions = new Hashtable<>();

        List<String> lefMacros = this.getSection(MACRO_REGEX, Parser.LEF_EXT);  // All Macros
        // Iterate over all Macros
        lefMacros.forEach(s -> {
            // Extract Macro Name
            String macroName;
            macroName = regexMatcher("MACRO.+", s).get(0).replaceAll("(MACRO|\\s+)", "");

            // Extract All the Pins block of each Macro
            StringBuilder allPins = new StringBuilder();
            regexMatcher("PIN\\s.+\\n(.+\\n)+\\s+END\\s+.+\\n", s).forEach(allPins::append);
            ArrayList<Pin> macroPins = getPins(allPins, layersSet);
            List<Rect> obs = getObs(s, layersSet);

            macrosDefinitions.put(macroName,new Macro(macroName, new Vector(0,0,0), macroPins, obs));
        });

        // Placed Pins
        ArrayList<Pin> pinList = new ArrayList<Pin>();
        regexMatcher( "-.*\\n+\\s*\\+\\s+LAYER.+\\n\\s+\\+\\s+PLACED.*;", regexMatcher("PINS\\s+\\d+(.*\\n)+END\\s+PINS", this.defFile).get(0)).forEach((pinBlock) -> {
            String[] delimited = pinBlock.split("\\s");
            int z = Character.digit(delimited[9].toCharArray()[delimited[9].length() - 1], 10);
            Rect rect = new Rect(new Vector(Double.parseDouble(delimited[11]) + Double.parseDouble(delimited[23]), Double.parseDouble(delimited[12]) + Double.parseDouble(delimited[24]), z), new Vector(Double.parseDouble(delimited[15]) + Double.parseDouble(delimited[23]), Double.parseDouble(delimited[16]) + Double.parseDouble(delimited[24]), z));
            List<Rect> rectList = new ArrayList<Rect>();
            rectList.add(rect);

            pinList.add(new Pin(rectList, delimited[1]));

        });
        List<Rect> obsList = new ArrayList<Rect>(); // Empty OBS
        macrosDefinitions.put("PIN", new Macro("PIN", new Vector(0, 0, 0), pinList, obsList));    // Pins rects

        return macrosDefinitions;
    }

    // Helper Function to get a given Macro's pins
    private ArrayList<Pin> getPins(StringBuilder allPins, Hashtable<String, Layer> layersSet) {
        // Extract Each Pins separately from the PINS Block
        ArrayList<Pin> macroPins = new ArrayList<>();

        for (String pin: allPins.toString().split("PIN")) { // Pins Loop
            if (!pin.isEmpty()) {
                String pinName = pin.split("\\n")[0].replaceAll(" ", "");
                // Get the Port of the individual Pin
                String port = pin.split("PORT")[1];

                macroPins.add(new Pin(getRects(port, layersSet), pinName));
            }
        }
        return macroPins;
    }

    // Helper Function to get a list of Rectangles
    private List<Rect> getRects(String port, Hashtable<String, Layer> layersSet){
        // Get each layer with its rectangles
        List<String> layers = regexMatcher("LAYER.+\\n(\\s+RECT.+)+", port);
        List<Rect> pinRects = new ArrayList<>();

        for (String layer : layers) {
            // Get Layer
            String layerName = regexMatcher("LAYER.+", layer).get(0).replaceAll("(LAYER|\\s+|;)", "");
            double layerNum = layersSet.get(layerName).getLayer();

            // Get the rectangles of the layer
            String rectDimensions = layer.split("LAYER.+")[1].replaceAll("(END.+|END\\n|;|\\n)", "").replaceAll("\\s+R", " R");

            double x1 = -1000000, x2 = -1000000, y1 = -1000000, y2 = -1000000;
            for (String rect : rectDimensions.split("RECT")) {   // Rects Loop
                if (!rect.isEmpty() && !rect.equals(" ")) {
                    int count = 0;
                    for (String coord : rect.split(" ")) {
                        if (!coord.isEmpty()) {
                            double num = Double.parseDouble(coord);
                            if (count == 0)
                                x1 = num;
                            else if (count == 1)
                                y1 = num;
                            else if (count == 2)
                                x2 = num;
                            else
                                y2 = num;
                            count++;
                        }
                    } // End of Each coordinate in a Rect loop
                    pinRects.add(new Rect(new Vector(x1, y1, layerNum), new Vector(x2, y2, layerNum)));
                }
            } // End of Each Rect in a Port loop
        }

        return pinRects;
    }


    private List<Rect> getObs(String macroBlock, Hashtable<String, Layer> layersSet) {
        if(macroBlock.contains("OBS")) {
            String obsBlock = regexMatcher("OBS\\s+.+\\n(.+\\n)+END", macroBlock).get(0);
            return getRects(obsBlock, layersSet);
        }
        return new ArrayList<Rect>();
    }


    private List<String> regexMatcher(String regex, String matcher) {
        List<String> matches = new ArrayList<>();
        Matcher m = Pattern.compile(regex).matcher(matcher);
        while(m.find())
            matches.add(m.group());

        return matches;
    }



    public Rect getDieArea ()
    {
        String die;
        List<String> coordList = new ArrayList<>();
        die = regexMatcher(DIEAREA_REGEX, this.defFile ).get(0).replaceAll("(DIEAREA|\\(|\\)|;)", "");
        String[] coords = die.split("\\s");
        for(String s: coords) {
            if(!s.isEmpty() && !s.equals(" "))
                coordList.add(s);
        }

        return new Rect(new Vector(Double.parseDouble(coordList.get(0)), Double.parseDouble(coordList.get(1)),0) , new Vector (Double.parseDouble(coordList.get(2)), Double.parseDouble(coordList.get(3)),0));
    }



     public Vector getCoreSite ()
    {
        String site;
        site = regexMatcher(SITE_REGEX, this.lefFile ).get(0);
        String[] coords = site.split("SIZE");
        coords = coords[1].split("\\t+")[1].split("\\s");

        return new Vector(Double.parseDouble(coords[0]), Double.parseDouble(coords[2])) ;
    }


     public HashSet <Net> getNets () {
        HashSet <Net> nets = new HashSet <> ();
        List <String> netsList;

        netsList = regexMatcher(NETS+SECTION_REGEX, this.defFile );
        String [] netsBlockArray = netsList.get(0).split("-");

        for (int i = 1 ; i < netsBlockArray.length; i++) { //loop at nets
            String[] netBlock = netsBlockArray[i].split("\n"); // split at end lines
            Net net = new Net(netBlock[0]);
            for (int j = 1; j < netBlock.length; j++) { //loop at lines
                if (!(i==  netsBlockArray.length-1 && j ==  netBlock.length-1) ){
                    String[] netLine = netBlock[j].replaceAll("(\\(|\\))", "").split("\\s");
                    net.insertPin(netLine[3], netLine[4]);
                }
            }
            nets.add(net);
        }
         return nets;
    }




     public HashSet <Net> getSpecialNets ()
     {
         HashSet <Net> Nets = new HashSet <> ();
         List <String> specialnetsList ;
         specialnetsList = regexMatcher(SPECNETS+SECTION_REGEX, this.defFile );
         String [] specialnetsBlockArray = specialnetsList.get(0).split(";");
        for (int i=1 ;i< specialnetsBlockArray.length-1 ; i++) //loop at every special net 
        {
            
          String[] specialnetBlock = specialnetsBlockArray[i].split("\n"); // split at end lines   
          Net net = new Net("SpecialNets");
          List<Rect> routingPath= new ArrayList<>();
          List <Via> viasList= new ArrayList<>() ;
          for (int j=2 ; j< specialnetBlock.length-1;j++) // loop at every line 
          {
             List <String> vias =  new ArrayList<>();
             
             if(!specialnetBlock[j].endsWith(")"))
             vias = regexMatcher("M[0-9]_M[0-9]", specialnetBlock[j] );
             else 
             vias.add("");
              
             List <String> metal_layer = regexMatcher("metal.", specialnetBlock[j] );
             String [] mlayer = metal_layer.get(0).split("metal"); //metal layer at [1]
             
             List <String> coordinates =  regexMatcher("\\(.+\\)", specialnetBlock[j] ); // (..) (..)
             String [] separateCoordinates = coordinates.get(0).split("\\)");
             Vector FirstCoordinates = null;
             Vector SecondCoordinates = null ;
             for ( int k=0 ; k<separateCoordinates.length ;k++) //loop at every point 
             {
                 separateCoordinates[k]= separateCoordinates[k].replaceAll("\\(", "");
                 String [] numbers = separateCoordinates[k].split(" ");
                 for (int l =0 ;l < numbers.length ; l++)
                 { 
                     if (!numbers[l].isEmpty() && numbers[l].equals("*"))
                     {
                         if (!numbers[l-1].equals("")) {
                          numbers[l] = numbers[l-1];
                         }
                         else 
                         {
                            numbers[l] =  Double.toString(FirstCoordinates.y) ;
                         }
                         
                     }                           
                }
                if (k == 0)
                   FirstCoordinates = new Vector (Double.parseDouble(numbers[1]) , Double.parseDouble(numbers[2]),Double.parseDouble(mlayer[1]));
                else
                   SecondCoordinates = new Vector (Double.parseDouble(numbers[2]) , Double.parseDouble(numbers[3]),Double.parseDouble(mlayer[1]));
                    
             }
             
             routingPath.add(new Rect (FirstCoordinates , SecondCoordinates));
             if (vias.get(0) != "")
             viasList.add(new Via (vias.get(0) , SecondCoordinates));
             else
             viasList.add(new Via (vias.get(0) , null)); 
          }
        
          String name = specialnetBlock[1].substring(2, specialnetBlock[1].length());
          net.insertSpecialPin(name, routingPath , viasList);
          Nets.add(net);
        } 
         return Nets ;
     }

     
     public Hashtable <Integer , Track> getTracks()
     {
         Hashtable <Integer , Track> rtrn  = new Hashtable() ;
         List <String> siteNumbers ;
         List <String> metalLayer ;
         List <String> trackDirection ;
         
         siteNumbers = regexMatcher("TRACKS.+STEP\\s+\\d+", this.defFile );
         metalLayer = regexMatcher("LAYER.+ ", this.defFile );
         trackDirection = regexMatcher("TRACKS\\s+.", this.defFile);
         for (int i =0 ;i<siteNumbers.size() ;i++) {
         String [] siteNumbers_Spaced = siteNumbers.get(i).split(" ");
         String [] metalLayer_Spaced = metalLayer.get(i).split(" ");
         String directionStr = trackDirection.get(i).replaceAll("TRACKS ", "");
         
         boolean direction;
            if(directionStr.equals("X"))
                direction = Track.X;
            else
                direction = Track.Y;

          rtrn.put( Integer.parseInt(metalLayer_Spaced[1].substring(metalLayer_Spaced[1].length()-1)), new Track(metalLayer_Spaced[1], Integer.parseInt(siteNumbers_Spaced[4]), direction, Integer.parseInt(siteNumbers_Spaced[6]), Integer.parseInt(siteNumbers_Spaced[2])));
         }

   
         
         return rtrn ;
     }

     public void UpdateDEFFile(String NetList){
        List<String> start = regexMatcher("(.*\\n)+\\nNETS", this.defFile);
         List<String> end = regexMatcher("\\nEND NETS(.*\\n)+", this.defFile);
         String file = start.get(0) + " " + NetList + end.get(0);
        System.out.println(file);
         try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(absolutePath + "/Parser/Resources/out.def"));
                 writer.write(file);
                 writer.close();
             } catch (IOException e) {
                 e.printStackTrace();
             }
     }
     
}
