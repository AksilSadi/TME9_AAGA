error id: file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java:java/util/ArrayList#get().
file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java
empty definition using pc, found symbol in pc: java/util/ArrayList#get().
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 5470
uri: file:///C:/Users/DELL/OneDrive/Bureau/sorbonne/M2/S2/AAGA/TME9/src/algorithms/DefaultTeam.java
text:
```scala
package algorithms;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class DefaultTeam {
  public ArrayList<Point> calculDominatingSet(ArrayList<Point> points, int edgeThreshold) {
    ArrayList<Point> universe = uniq(points);
    if (universe.isEmpty()) {
      saveToFile("output", universe);
      return universe;
    }

    int n = universe.size();
    ArrayList<ArrayList<Integer>> closed = buildClosedNeighborhood(universe, edgeThreshold);
    ArrayList<ArrayList<Integer>> dominatedBy = buildDominatedBy(closed);
    Random rng = new Random(20260226L);
    long deadline = System.currentTimeMillis() + 4500L;

    ArrayList<Integer> bestD = null;
    int starts = 0;
    while (starts < 100 && System.currentTimeMillis() < deadline) {
      ArrayList<Integer> d = greedyMaxCoverage(closed, dominatedBy, rng, 10);
      boolean[] inD = new boolean[n];
      for (int v : d) inD[v] = true;
      int[] posInD = new int[n];
      Arrays.fill(posInD, -1);
      for (int i = 0; i < d.size(); i++) posInD[d.get(i)] = i;
      int[] domCount = buildDomCount(d, closed, n);

      pruneDominators(d, inD, posInD, domCount, closed, rng);

      int stagnation = 0;
      while (stagnation < 10 && System.currentTimeMillis() < deadline) {
        int before = d.size();
        boolean improved = false;

        if (tryMoveTwoToOne(d, inD, posInD, domCount, closed, universe, edgeThreshold, deadline)) {
          pruneDominators(d, inD, posInD, domCount, closed, rng);
          improved = d.size() < before;
        } else if (tryMoveOneToOneSwap(d, inD, posInD, domCount, closed, rng, deadline)) {
          int afterSwap = d.size();
          pruneDominators(d, inD, posInD, domCount, closed, rng);
          improved = d.size() < afterSwap;
        }

        if (improved) stagnation = 0;
        else stagnation++;
      }

      if (bestD == null || d.size() < bestD.size()) bestD = new ArrayList<Integer>(d);
      starts++;
    }

    if (bestD == null) bestD = greedyMaxCoverage(closed, dominatedBy, rng, 1);
    ArrayList<Point> best = indicesToPoints(bestD, universe);
    saveToFile("output", best);
    return best;
  }

  public ArrayList<ArrayList<Integer>> buildClosedNeighborhood(ArrayList<Point> points, int edgeThreshold) {
    int n = points.size();
    long thr2 = (long) edgeThreshold * (long) edgeThreshold;
    ArrayList<ArrayList<Integer>> closed = new ArrayList<ArrayList<Integer>>(n);
    for (int i = 0; i < n; i++) closed.add(new ArrayList<Integer>());
    for (int i = 0; i < n; i++) {
      Point pi = points.get(i);
      int xi = pi.x;
      int yi = pi.y;
      for (int j = 0; j < n; j++) {
        Point pj = points.get(j);
        long dx = (long) xi - (long) pj.x;
        long dy = (long) yi - (long) pj.y;
        if (dx * dx + dy * dy <= thr2) closed.get(i).add(j);
      }
    }
    return closed;
  }

  public ArrayList<ArrayList<Integer>> buildDominatedBy(ArrayList<ArrayList<Integer>> closed) {
    int n = closed.size();
    ArrayList<ArrayList<Integer>> dominatedBy = new ArrayList<ArrayList<Integer>>(n);
    for (int i = 0; i < n; i++) dominatedBy.add(new ArrayList<Integer>());
    for (int i = 0; i < n; i++) for (int x : closed.get(i)) dominatedBy.get(x).add(i);
    return dominatedBy;
  }

  public ArrayList<Integer> greedyMaxCoverage(
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<ArrayList<Integer>> dominatedBy,
      Random rng,
      int topK) {
    int n = closed.size();
    boolean[] dominated = new boolean[n];
    boolean[] inD = new boolean[n];
    int[] gain = new int[n];
    for (int i = 0; i < n; i++) gain[i] = closed.get(i).size();
    int undomCount = n;
    ArrayList<Integer> d = new ArrayList<Integer>();

    while (undomCount > 0) {
      int[] topIdx = new int[] {-1, -1, -1};
      int[] topGain = new int[] {-1, -1, -1};

      for (int i = 0; i < n; i++) {
        if (inD[i]) continue;
        int g = gain[i];

        if (g > topGain[0]) {
          topGain[2] = topGain[1];
          topIdx[2] = topIdx[1];
          topGain[1] = topGain[0];
          topIdx[1] = topIdx[0];
          topGain[0] = g;
          topIdx[0] = i;
        } else if (g > topGain[1]) {
          topGain[2] = topGain[1];
          topIdx[2] = topIdx[1];
          topGain[1] = g;
          topIdx[1] = i;
        } else if (g > topGain[2]) {
          topGain[2] = g;
          topIdx[2] = i;
        }
      }

      ArrayList<Integer> candidates = new ArrayList<Integer>();
      for (int k = 0; k < topK; k++) if (topIdx[k] != -1 && topGain[k] > 0) candidates.add(topIdx[k]);
      if (candidates.isEmpty()) break;

      int chosen = candidates.get(rng.nextInt(candidates.size()));
      d.add(chosen);
      inD[chosen] = true;
      for (int x : closed.get(chosen)) {
        if (!dominated[x]) {
          dominated[x] = true;
          undomCount--;
          for (int i : dominatedBy.get(x)) if (!inD[i]) gain[i]--;
        }
      }
    }

    return d;
  }

  public int[] buildDomCount(ArrayList<Integer> d, ArrayList<ArrayList<Integer>> closed, int n) {
    int[] domCount = new int[n];
    for (int v : d) for (int x : closed.ge@@t(v)) domCount[x]++;
    return domCount;
  }

  public void pruneDominators(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      Random rng) {
    ArrayList<Integer> order = new ArrayList<Integer>(d);
    Collections.shuffle(order, rng);
    for (int v : order) {
      if (!inD[v]) continue;
      boolean removable = true;
      for (int x : closed.get(v)) {
        if (domCount[x] <= 1) {
          removable = false;
          break;
        }
      }
      if (!removable) continue;
      removeFromD(d, posInD, v);
      inD[v] = false;
      for (int x : closed.get(v)) domCount[x]--;
    }
  }

  public boolean tryMoveTwoToOne(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<Point> points,
      int edgeThreshold,
      long deadline) {

    if (d.size() < 2) return false;
    int n = closed.size();
    double pairRadius = 2.6 * edgeThreshold;
    double pairRadius2 = pairRadius * pairRadius;
    int[] markCand = new int[n];
    int[] markTouched = new int[n];
    int curCand = 1;
    int curTouched = 1;
    ArrayList<Integer> intersection = new ArrayList<Integer>();
    ArrayList<Integer> union = new ArrayList<Integer>();
    ArrayList<Integer> touched = new ArrayList<Integer>();
    ArrayList<Integer> candidates = new ArrayList<Integer>();

    for (int i = 0; i < d.size(); i++) {
      if (System.currentTimeMillis() >= deadline) return false;
      int u = d.get(i);
      Point pu = points.get(u);
      for (int j = i + 1; j < d.size(); j++) {
        int v = d.get(j);
        Point pv = points.get(v);
        double dx = pu.x - pv.x;
        double dy = pu.y - pv.y;
        if (dx * dx + dy * dy > pairRadius2) continue;

        curCand++;
        curTouched++;
        intersection.clear();
        union.clear();
        touched.clear();
        candidates.clear();

        for (int x : closed.get(u)) {
          if (markCand[x] != curCand) {
            markCand[x] = curCand;
            union.add(x);
          }
          if (markTouched[x] != curTouched) {
            markTouched[x] = curTouched;
            touched.add(x);
          }
        }
        for (int x : closed.get(v)) {
          if (markCand[x] == curCand) intersection.add(x);
          else {
            markCand[x] = curCand;
            union.add(x);
          }
          if (markTouched[x] != curTouched) {
            markTouched[x] = curTouched;
            touched.add(x);
          }
        }

        for (int s : intersection) if (!inD[s]) candidates.add(s);
        if (candidates.isEmpty()) for (int s : union) if (!inD[s]) candidates.add(s);

        for (int s : candidates) {

          for (int x : closed.get(u)) domCount[x]--;
          for (int x : closed.get(v)) domCount[x]--;
          for (int x : closed.get(s)) domCount[x]++;

          boolean valid = true;
          for (int x : touched) {
            if (domCount[x] <= 0) {
              valid = false;
              break;
            }
          }

          if (valid) {
            removeFromD(d, posInD, u);
            inD[u] = false;
            removeFromD(d, posInD, v);
            inD[v] = false;
            addToD(d, posInD, s);
            inD[s] = true;
            return true;
          }

          for (int x : closed.get(s)) domCount[x]--;
          for (int x : closed.get(v)) domCount[x]++;
          for (int x : closed.get(u)) domCount[x]++;
        }
      }
    }
    return false;
  }

  public boolean tryMoveOneToOneSwap(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      Random rng,
      long deadline) {
    if (d.isEmpty()) return false;
    int n = closed.size();
    int tries = Math.min(80, n);

    for (int t = 0; t < tries; t++) {
      if (System.currentTimeMillis() >= deadline) return false;
      int u = d.get(rng.nextInt(d.size()));

      int s = -1;
      ArrayList<Integer> local = closed.get(u);
      for (int k = 0; k < local.size(); k++) {
        int c = local.get(rng.nextInt(local.size()));
        if (!inD[c]) {
          s = c;
          break;
        }
      }
      if (s == -1) {
        for (int k = 0; k < 20; k++) {
          int c = rng.nextInt(n);
          if (!inD[c]) {
            s = c;
            break;
          }
        }
      }
      if (s == -1) continue;

      for (int x : closed.get(u)) domCount[x]--;
      for (int x : closed.get(s)) domCount[x]++;

      boolean valid = true;
      for (int x : closed.get(u)) {
        if (domCount[x] <= 0) {
          valid = false;
          break;
        }
      }

      if (valid) {
        removeFromD(d, posInD, u);
        addToD(d, posInD, s);
        inD[u] = false;
        inD[s] = true;
        return true;
      }

      for (int x : closed.get(s)) domCount[x]--;
      for (int x : closed.get(u)) domCount[x]++;
    }
    return false;
  }

  public void addToD(ArrayList<Integer> d, int[] posInD, int v) {
    posInD[v] = d.size();
    d.add(v);
  }

  public void removeFromD(ArrayList<Integer> d, int[] posInD, int v) {
    int pos = posInD[v];
    if (pos < 0) return;
    int lastPos = d.size() - 1;
    int last = d.get(lastPos);
    d.set(pos, last);
    posInD[last] = pos;
    d.remove(lastPos);
    posInD[v] = -1;
  }

  public ArrayList<Point> indicesToPoints(ArrayList<Integer> idx, ArrayList<Point> points) {
    ArrayList<Point> result = new ArrayList<Point>();
    for (int i : idx) result.add(points.get(i));
    return result;
  }

  public int neighbours(Point p, ArrayList<Point> points, int edgeThreshold) {
    int res=-1;
    for (Point q: points) if (p.distance(q)<=edgeThreshold) res++;
    return res;
  }

  public ArrayList<Point> uniq(ArrayList<Point> points) {
    return new ArrayList<Point>(new LinkedHashSet<Point>(points));
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

empty definition using pc, found symbol in pc: java/util/ArrayList#get().