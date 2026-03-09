error id: file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java:java/util/ArrayList#
file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java
empty definition using pc, found symbol in pc: java/util/ArrayList#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 516
uri: file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java
text:
```scala
package algorithms;

import java.awt.Point;
import java.util.ArrayList;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class DefaultTeam {
  public ArrayList<Point> calculDominatingSet(ArrayList<Point> points, int edgeThreshold) {
    ArrayList<Point> rest = (ArrayList<Point>)points.clone();
    ArrayList<Point> result = new Arr@@ayList<Point>();

    while (!rest.isEmpty()){
      Point kimK=rest.get(0);
      for (Point p: rest)
        if (neighbours(p,rest,edgeThreshold)>neighbours(kimK,rest,edgeThreshold))
          kimK=p;
      result.add(kimK);
      for (int i=0;i<rest.size();i++)
        if (rest.get(i).distance(kimK)<=edgeThreshold){
          rest.remove(i);
          i--;
        }
    }
    saveToFile("output",result);
    return result;
  }
  public int neighbours(Point p, ArrayList<Point> points, int edgeThreshold) {
    int res=-1;
    for (Point q: points) if (p.distance(q)<=edgeThreshold) res++;
    return res;
  }


  //FILE PRINTER
  private void saveToFile(String filename,ArrayList<Point> result){
    int index=0;
    try {
      while(true){
        BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(filename+Integer.toString(index)+".points")));
        try {
          input.close();
        } catch (IOException e) {
          System.err.println("I/O exception: unable to close "+filename+Integer.toString(index)+".points");
        }
        index++;
      }
    } catch (FileNotFoundException e) {
      printToFile(filename+Integer.toString(index)+".points",result);
    }
  }
  private void printToFile(String filename,ArrayList<Point> points){
    try {
      PrintStream output = new PrintStream(new FileOutputStream(filename));
      int x,y;
      for (Point p:points) output.println(Integer.toString((int)p.getX())+" "+Integer.toString((int)p.getY()));
      output.close();
    } catch (FileNotFoundException e) {
      System.err.println("I/O exception: unable to create "+filename);
    }
  }

  //FILE LOADER
  private ArrayList<Point> readFromFile(String filename) {
    String line;
    String[] coordinates;
    ArrayList<Point> points=new ArrayList<Point>();
    try {
      BufferedReader input = new BufferedReader(
          new InputStreamReader(new FileInputStream(filename))
          );
      try {
        while ((line=input.readLine())!=null) {
          coordinates=line.split("\\s+");
          points.add(new Point(Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1])));
        }
      } catch (IOException e) {
        System.err.println("Exception: interrupted I/O.");
      } finally {
        try {
          input.close();
        } catch (IOException e) {
          System.err.println("I/O exception: unable to close "+filename);
        }
      }
    } catch (FileNotFoundException e) {
      System.err.println("Input file not found.");
         }
    return points;
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/util/ArrayList#