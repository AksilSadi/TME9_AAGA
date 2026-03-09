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
  private static final int PAIR_LIMIT = 96;
  private static final int TWO_TO_ONE_TEST_LIMIT = 16;
  private static final int THREE_TO_TWO_CAND_LIMIT = 10;

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
      ArrayList<Integer> d;
      if (bestD != null && rng.nextDouble() < 0.30) d = new ArrayList<Integer>(bestD);
      else d = greedyMaxCoverage(closed, dominatedBy, rng, 10);
      boolean[] inD = new boolean[n];
      for (int v : d) inD[v] = true;
      int[] posInD = new int[n];
      Arrays.fill(posInD, -1);
      for (int i = 0; i < d.size(); i++) posInD[d.get(i)] = i;
      int[] domCount = buildDomCount(d, closed, n);

      postImprove(d, inD, posInD, domCount, closed, rng, 10);

      int stagnation = 0;
      while (stagnation < 10 && System.currentTimeMillis() < deadline) {
        int before = d.size();
        boolean improved = false;

        if (tryMoveTwoToZero(d, inD, posInD, domCount, closed, universe, edgeThreshold, deadline)) {
          postImprove(d, inD, posInD, domCount, closed, rng, 8);
          improved = d.size() < before;
        } else if (tryMoveTwoToOne(d, inD, posInD, domCount, closed, universe, edgeThreshold, deadline)) {
          postImprove(d, inD, posInD, domCount, closed, rng, 8);
          improved = d.size() < before;
        } else if (tryMoveThreeToTwo(d, inD, posInD, domCount, closed, universe, edgeThreshold, deadline)) {
          postImprove(d, inD, posInD, domCount, closed, rng, 10);
          improved = d.size() < before;
        } else if (stagnation >= 3 && trySwapChainUnlock(d, inD, posInD, domCount, closed, universe, edgeThreshold, rng, deadline)) {
          postImprove(d, inD, posInD, domCount, closed, rng, 8);
          improved = d.size() < before;
        } else if (tryMoveOneToOneSwap(d, inD, posInD, domCount, closed, rng, deadline)) {
          int afterSwap = d.size();
          postImprove(d, inD, posInD, domCount, closed, rng, 6);
          improved = d.size() < afterSwap;
        } else if (kickRepairPrune(d, inD, posInD, domCount, closed, dominatedBy, rng, deadline)) {
          int afterKick = d.size();
          postImprove(d, inD, posInD, domCount, closed, rng, 8);
          improved = d.size() < afterKick || d.size() < before;
        } else if (shortPruneSwapCycle(d, inD, posInD, domCount, closed, rng, deadline)) {
          improved = d.size() < before;
        }

        if (improved) stagnation = 0;
        else stagnation++;
      }

      if (bestD == null || d.size() < bestD.size()) bestD = new ArrayList<Integer>(d);
      starts++;
    }

    if (bestD == null) bestD = greedyMaxCoverage(closed, dominatedBy, rng, 6);
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
    int kEff = Math.max(1, Math.min(topK, n));
    boolean[] dominated = new boolean[n];
    boolean[] inD = new boolean[n];
    int[] gain = new int[n];
    for (int i = 0; i < n; i++) gain[i] = closed.get(i).size();
    int undomCount = n;
    ArrayList<Integer> d = new ArrayList<Integer>();

    while (undomCount > 0) {
      int[] topIdx = new int[kEff];
      int[] topGain = new int[kEff];
      Arrays.fill(topIdx, -1);
      Arrays.fill(topGain, -1);

      for (int i = 0; i < n; i++) {
        if (inD[i]) continue;
        int g = gain[i];
        int overlap = closed.get(i).size() - g;
        int score = g * 100 - overlap;
        if (score <= topGain[kEff - 1]) continue;
        int pos = kEff - 1;
        while (pos > 0 && score > topGain[pos - 1]) {
          topGain[pos] = topGain[pos - 1];
          topIdx[pos] = topIdx[pos - 1];
          pos--;
        }
        topGain[pos] = score;
        topIdx[pos] = i;
      }

      ArrayList<Integer> candidates = new ArrayList<Integer>();
      for (int k = 0; k < kEff; k++) if (topIdx[k] != -1 && topGain[k] > 0) candidates.add(topIdx[k]);
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
    for (int v : d) for (int x : closed.get(v)) domCount[x]++;
    return domCount;
  }

  public boolean pruneDominators(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      Random rng) {
    boolean removedAny = false;
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
      removedAny = true;
    }
    return removedAny;
  }

  public void postImprove(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      Random rng,
      int passes) {
    while (pruneTargetedRemovable(d, inD, posInD, domCount, closed)) {}
    for (int i = 0; i < passes; i++) {
      if (!pruneDominators(d, inD, posInD, domCount, closed, rng)) break;
    }
  }

  public boolean pruneTargetedRemovable(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed) {
    boolean removedAny = false;
    boolean progress = true;
    while (progress) {
      progress = false;
      for (int i = 0; i < d.size(); i++) {
        int v = d.get(i);
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
        removedAny = true;
        progress = true;
        i--;
      }
    }
    return removedAny;
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
    int[] markCand = new int[n];
    int[] markTouched = new int[n];
    int[] markCritical = new int[n];
    int curCand = 1;
    int curTouched = 1;
    int curCritical = 1;
    ArrayList<Integer> intersection = new ArrayList<Integer>();
    ArrayList<Integer> union = new ArrayList<Integer>();
    ArrayList<Integer> touched = new ArrayList<Integer>();
    ArrayList<Integer> candidates = new ArrayList<Integer>();
    int testLimit = TWO_TO_ONE_TEST_LIMIT;
    int[] bestCand = new int[testLimit];
    int[] bestScore = new int[testLimit];
    int[] pairU = new int[PAIR_LIMIT];
    int[] pairV = new int[PAIR_LIMIT];
    int pairCount = buildOrderedPairs(d, closed, points, edgeThreshold, domCount, pairU, pairV, 6, 4);

    for (int p = 0; p < pairCount; p++) {
      if (System.currentTimeMillis() >= deadline) return false;
      int u = pairU[p];
      int v = pairV[p];

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
      curCritical++;
      for (int x : touched) if (domCount[x] <= 2) markCritical[x] = curCritical;

      Arrays.fill(bestCand, -1);
      Arrays.fill(bestScore, -1);
      for (int s : candidates) {
        int score = 0;
        for (int x : closed.get(s)) {
          if (markCritical[x] == curCritical) {
            score += 8;
            if (domCount[x] <= 1) score += 10;
          } else if (markTouched[x] == curTouched) score += 1;
        }
        if (score <= bestScore[testLimit - 1]) continue;
        int pos = testLimit - 1;
        while (pos > 0 && score > bestScore[pos - 1]) {
          bestScore[pos] = bestScore[pos - 1];
          bestCand[pos] = bestCand[pos - 1];
          pos--;
        }
        bestScore[pos] = score;
        bestCand[pos] = s;
      }

      for (int idx = 0; idx < testLimit; idx++) {
        int s = bestCand[idx];
        if (s == -1) break;

        applyMoveDelta(domCount, closed, u, v, s);
        boolean valid = allTouchedDominated(domCount, touched);
        if (valid) {
          commitMove(d, inD, posInD, u, v, s);
          return true;
        }
        rollbackMoveDelta(domCount, closed, u, v, s);
      }
    }
    return false;
  }

  public boolean tryMoveThreeToTwo(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<Point> points,
      int edgeThreshold,
      long deadline) {
    if (d.size() < 3) return false;
    int n = closed.size();
    int[] pairU = new int[PAIR_LIMIT];
    int[] pairV = new int[PAIR_LIMIT];
    int pairCount = buildOrderedPairs(d, closed, points, edgeThreshold, domCount, pairU, pairV, 6, 4);
    int[] markW = new int[n];
    int[] markTouched = new int[n];
    int[] markCand = new int[n];
    int[] markCritical = new int[n];
    int curW = 1;
    int curTouched = 1;
    int curCand = 1;
    int curCritical = 1;
    ArrayList<Integer> wList = new ArrayList<Integer>();
    ArrayList<Integer> touched = new ArrayList<Integer>();
    ArrayList<Integer> candidates = new ArrayList<Integer>();
    int[] topCand = new int[THREE_TO_TWO_CAND_LIMIT];
    int[] topScore = new int[THREE_TO_TWO_CAND_LIMIT];

    for (int p = 0; p < pairCount; p++) {
      if (System.currentTimeMillis() >= deadline) return false;
      int u = pairU[p];
      int v = pairV[p];
      curW++;
      wList.clear();
      for (int x : closed.get(u)) if (inD[x] && x != u && x != v && markW[x] != curW) {
        markW[x] = curW;
        wList.add(x);
      }
      for (int x : closed.get(v)) if (inD[x] && x != u && x != v && markW[x] != curW) {
        markW[x] = curW;
        wList.add(x);
      }
      int wTest = Math.min(8, wList.size());
      for (int wi = 0; wi < wTest; wi++) {
        int w = wList.get(wi);
        curTouched++;
        curCand++;
        curCritical++;
        touched.clear();
        candidates.clear();
        for (int x : closed.get(u)) {
          if (markTouched[x] != curTouched) {
            markTouched[x] = curTouched;
            touched.add(x);
          }
          if (!inD[x]) markCand[x] = curCand;
        }
        for (int x : closed.get(v)) {
          if (markTouched[x] != curTouched) {
            markTouched[x] = curTouched;
            touched.add(x);
          }
          if (!inD[x]) markCand[x] = curCand;
        }
        for (int x : closed.get(w)) {
          if (markTouched[x] != curTouched) {
            markTouched[x] = curTouched;
            touched.add(x);
          }
          if (!inD[x]) markCand[x] = curCand;
        }
        for (int x : touched) if (domCount[x] <= 3) markCritical[x] = curCritical;
        for (int s = 0; s < n; s++) if (markCand[s] == curCand) candidates.add(s);

        Arrays.fill(topCand, -1);
        Arrays.fill(topScore, -1);
        for (int s : candidates) {
          int sc = 0;
          for (int x : closed.get(s)) {
            if (markCritical[x] == curCritical) sc += 6;
            else if (markTouched[x] == curTouched) sc += 1;
          }
          if (sc <= topScore[THREE_TO_TWO_CAND_LIMIT - 1]) continue;
          int pos = THREE_TO_TWO_CAND_LIMIT - 1;
          while (pos > 0 && sc > topScore[pos - 1]) {
            topScore[pos] = topScore[pos - 1];
            topCand[pos] = topCand[pos - 1];
            pos--;
          }
          topScore[pos] = sc;
          topCand[pos] = s;
        }

        for (int aIdx = 0; aIdx < THREE_TO_TWO_CAND_LIMIT; aIdx++) {
          int a = topCand[aIdx];
          if (a < 0) break;
          for (int bIdx = aIdx + 1; bIdx < THREE_TO_TWO_CAND_LIMIT; bIdx++) {
            int b = topCand[bIdx];
            if (b < 0 || b == a) continue;
            for (int x : closed.get(u)) domCount[x]--;
            for (int x : closed.get(v)) domCount[x]--;
            for (int x : closed.get(w)) domCount[x]--;
            for (int x : closed.get(a)) domCount[x]++;
            for (int x : closed.get(b)) domCount[x]++;
            boolean valid = allTouchedDominated(domCount, touched);
            if (valid) {
              removeFromD(d, posInD, u);
              inD[u] = false;
              removeFromD(d, posInD, v);
              inD[v] = false;
              removeFromD(d, posInD, w);
              inD[w] = false;
              addToD(d, posInD, a);
              inD[a] = true;
              addToD(d, posInD, b);
              inD[b] = true;
              return true;
            }
            for (int x : closed.get(b)) domCount[x]--;
            for (int x : closed.get(a)) domCount[x]--;
            for (int x : closed.get(w)) domCount[x]++;
            for (int x : closed.get(v)) domCount[x]++;
            for (int x : closed.get(u)) domCount[x]++;
          }
        }
      }
    }
    return false;
  }

  public boolean tryMoveTwoToZero(
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
    int[] markTouched = new int[n];
    int curTouched = 1;
    ArrayList<Integer> touched = new ArrayList<Integer>();
    int[] pairU = new int[PAIR_LIMIT];
    int[] pairV = new int[PAIR_LIMIT];
    int pairCount = buildOrderedPairs(d, closed, points, edgeThreshold, domCount, pairU, pairV, 8, 6);

    for (int p = 0; p < pairCount; p++) {
      if (System.currentTimeMillis() >= deadline) return false;
      int u = pairU[p];
      int v = pairV[p];
      curTouched++;
      touched.clear();

      for (int x : closed.get(u)) {
        if (markTouched[x] != curTouched) {
          markTouched[x] = curTouched;
          touched.add(x);
        }
      }
      for (int x : closed.get(v)) {
        if (markTouched[x] != curTouched) {
          markTouched[x] = curTouched;
          touched.add(x);
        }
      }

      applyMoveDelta(domCount, closed, u, v, -1);
      boolean valid = allTouchedDominated(domCount, touched);
      if (valid) {
        commitMove(d, inD, posInD, u, v, -1);
        return true;
      }
      rollbackMoveDelta(domCount, closed, u, v, -1);
    }
    return false;
  }

  public void applyMoveDelta(int[] domCount, ArrayList<ArrayList<Integer>> closed, int u, int v, int s) {
    for (int x : closed.get(u)) domCount[x]--;
    for (int x : closed.get(v)) domCount[x]--;
    if (s >= 0) for (int x : closed.get(s)) domCount[x]++;
  }

  public void rollbackMoveDelta(int[] domCount, ArrayList<ArrayList<Integer>> closed, int u, int v, int s) {
    if (s >= 0) for (int x : closed.get(s)) domCount[x]--;
    for (int x : closed.get(v)) domCount[x]++;
    for (int x : closed.get(u)) domCount[x]++;
  }

  public boolean allTouchedDominated(int[] domCount, ArrayList<Integer> touched) {
    for (int x : touched) if (domCount[x] <= 0) return false;
    return true;
  }

  public void commitMove(ArrayList<Integer> d, boolean[] inD, int[] posInD, int u, int v, int s) {
    removeFromD(d, posInD, u);
    inD[u] = false;
    removeFromD(d, posInD, v);
    inD[v] = false;
    if (s >= 0) {
      addToD(d, posInD, s);
      inD[s] = true;
    }
  }

  public int buildOrderedPairs(
      ArrayList<Integer> d,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<Point> points,
      int edgeThreshold,
      int[] domCount,
      int[] outU,
      int[] outV,
      int overlapWeight,
      int fragilePenalty) {
    int limit = outU.length;
    int[] outScore = new int[limit];
    Arrays.fill(outScore, Integer.MIN_VALUE);
    int[] mark = new int[closed.size()];
    int curMark = 1;
    double pairRadius = 2.6 * edgeThreshold;
    double pairRadius2 = pairRadius * pairRadius;
    int count = 0;

    for (int i = 0; i < d.size(); i++) {
      int u = d.get(i);
      Point pu = points.get(u);
      int fragileU = 0;
      for (int x : closed.get(u)) if (domCount[x] == 1) fragileU++;
      for (int j = i + 1; j < d.size(); j++) {
        int v = d.get(j);
        Point pv = points.get(v);
        double dx = pu.x - pv.x;
        double dy = pu.y - pv.y;
        if (dx * dx + dy * dy > pairRadius2) continue;

        curMark++;
        for (int x : closed.get(u)) mark[x] = curMark;
        int overlap = 0;
        int fragileV = 0;
        for (int x : closed.get(v)) {
          if (mark[x] == curMark) overlap++;
          if (domCount[x] == 1) fragileV++;
        }

        int score = overlap * overlapWeight + (closed.get(u).size() + closed.get(v).size()) - fragilePenalty * (fragileU + fragileV);
        if (count < limit) {
          outU[count] = u;
          outV[count] = v;
          outScore[count] = score;
          count++;
        } else if (score > outScore[limit - 1]) {
          outU[limit - 1] = u;
          outV[limit - 1] = v;
          outScore[limit - 1] = score;
        } else {
          continue;
        }

        int k = Math.min(count, limit) - 1;
        while (k > 0 && outScore[k] > outScore[k - 1]) {
          int tmpS = outScore[k - 1];
          outScore[k - 1] = outScore[k];
          outScore[k] = tmpS;
          int tmpU = outU[k - 1];
          outU[k - 1] = outU[k];
          outU[k] = tmpU;
          int tmpV = outV[k - 1];
          outV[k - 1] = outV[k];
          outV[k] = tmpV;
          k--;
        }
      }
    }

    return Math.min(count, limit);
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

  public boolean trySwapChainUnlock(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<Point> points,
      int edgeThreshold,
      Random rng,
      long deadline) {
    if (System.currentTimeMillis() >= deadline) return false;
    for (int i = 0; i < 3 && System.currentTimeMillis() < deadline; i++) {
      tryMoveOneToOneSwap(d, inD, posInD, domCount, closed, rng, deadline);
    }
    if (tryMoveTwoToZero(d, inD, posInD, domCount, closed, points, edgeThreshold, deadline)) return true;
    if (tryMoveTwoToOne(d, inD, posInD, domCount, closed, points, edgeThreshold, deadline)) return true;
    return tryMoveThreeToTwo(d, inD, posInD, domCount, closed, points, edgeThreshold, deadline);
  }

  public boolean shortPruneSwapCycle(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      Random rng,
      long deadline) {
    int before = d.size();
    for (int i = 0; i < 3 && System.currentTimeMillis() < deadline; i++) {
      postImprove(d, inD, posInD, domCount, closed, rng, 2);
      tryMoveOneToOneSwap(d, inD, posInD, domCount, closed, rng, deadline);
      postImprove(d, inD, posInD, domCount, closed, rng, 2);
      if (d.size() < before) return true;
    }
    return false;
  }

  public boolean kickRepairPrune(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<ArrayList<Integer>> dominatedBy,
      Random rng,
      long deadline) {
    if (d.size() <= 1 || System.currentTimeMillis() >= deadline) return false;

    ArrayList<Integer> backupD = new ArrayList<Integer>(d);
    boolean[] backupInD = inD.clone();
    int[] backupPos = posInD.clone();
    int[] backupDom = domCount.clone();
    int oldSize = d.size();

    int removeCount = (rng.nextDouble() < 0.20 && d.size() > 2) ? 2 : 1;
    int maxAdds = (removeCount == 2) ? 3 : 2;

    for (int r = 0; r < removeCount; r++) {
      int removed = pickWeakDominant(d, domCount, closed, rng);
      if (removed == -1) break;
      removeFromD(d, posInD, removed);
      inD[removed] = false;
      for (int x : closed.get(removed)) domCount[x]--;
    }

    repairGreedyLimited(d, inD, posInD, domCount, closed, dominatedBy, rng, maxAdds);
    if (!isFullyDominated(domCount)) {
      d.clear();
      d.addAll(backupD);
      System.arraycopy(backupInD, 0, inD, 0, inD.length);
      System.arraycopy(backupPos, 0, posInD, 0, posInD.length);
      System.arraycopy(backupDom, 0, domCount, 0, domCount.length);
      return false;
    }

    return d.size() <= oldSize + 1;
  }

  public int pickWeakDominant(ArrayList<Integer> d, int[] domCount, ArrayList<ArrayList<Integer>> closed, Random rng) {
    if (d.isEmpty()) return -1;
    int candidateCount = Math.min(8, d.size());
    int best = -1;
    int bestFragile = Integer.MAX_VALUE;
    for (int t = 0; t < candidateCount; t++) {
      int v = d.get(rng.nextInt(d.size()));
      int fragile = 0;
      for (int x : closed.get(v)) if (domCount[x] <= 1) fragile++;
      if (fragile < bestFragile) {
        bestFragile = fragile;
        best = v;
      }
    }
    return best;
  }

  public void repairGreedyLimited(
      ArrayList<Integer> d,
      boolean[] inD,
      int[] posInD,
      int[] domCount,
      ArrayList<ArrayList<Integer>> closed,
      ArrayList<ArrayList<Integer>> dominatedBy,
      Random rng,
      int maxAdds) {
    int n = closed.size();
    int added = 0;
    while (!isFullyDominated(domCount) && added < maxAdds) {
      int best = -1;
      int bestGain = -1;
      int[] top = new int[] {-1, -1, -1};
      int[] topGain = new int[] {-1, -1, -1};

      for (int x = 0; x < n; x++) {
        if (domCount[x] > 0) continue;
        for (int c : dominatedBy.get(x)) {
          if (inD[c]) continue;
          int gain = 0;
          for (int y : closed.get(c)) if (domCount[y] <= 0) gain++;
          if (gain > bestGain) {
            bestGain = gain;
            best = c;
          }
        }
      }

      for (int c = 0; c < n; c++) {
        if (inD[c]) continue;
        int gain = 0;
        for (int y : closed.get(c)) if (domCount[y] <= 0) gain++;
        if (gain > topGain[0]) {
          topGain[2] = topGain[1];
          top[2] = top[1];
          topGain[1] = topGain[0];
          top[1] = top[0];
          topGain[0] = gain;
          top[0] = c;
        } else if (gain > topGain[1]) {
          topGain[2] = topGain[1];
          top[2] = top[1];
          topGain[1] = gain;
          top[1] = c;
        } else if (gain > topGain[2]) {
          topGain[2] = gain;
          top[2] = c;
        }
      }

      if (best == -1) best = top[0];
      if (top[1] != -1 && top[2] != -1) {
        int pick = top[rng.nextInt(3)];
        if (pick != -1) best = pick;
      } else if (top[1] != -1) {
        int pick = top[rng.nextInt(2)];
        if (pick != -1) best = pick;
      }
      if (best == -1) break;

      addToD(d, posInD, best);
      inD[best] = true;
      for (int y : closed.get(best)) domCount[y]++;
      added++;
    }
  }

  public boolean isFullyDominated(int[] domCount) {
    for (int x : domCount) if (x <= 0) return false;
    return true;
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
      for (Point p:points) output.println(Integer.toString((int)p.getX())+" "+Integer.toString((int)p.getY()));
      output.close();
    } catch (FileNotFoundException e) {
      System.err.println("I/O exception: unable to create "+filename);
    }
  }
}
