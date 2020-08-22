package signpost;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Arrays;

import static signpost.Place.*;
import static signpost.Utils.*;

/** The state of a Signpost puzzle.  Each cell has coordinates (x, y),
 *  where 0 <= x < width(), 0 <= y < height().  The upper-left corner
 *  of the puzzle has coordinates (0, height() - 1), and the lower-right
 *  corner is at (width() - 1, 0).
 *
 *  A constructor initializes the squares according to a particular
 *  solution.  A solution is an assignment of sequence numbers from 1
 *  to size() == width() * height() to square positions so that
 *  squares with adjacent numbers are separated by queen moves. A
 *  queen move is a move from one square to another horizontally,
 *  vertically, or diagonally. The effect is to give each square whose
 *  number in the solution is less than size() an <i>arrow
 *  direction</i>, 1 <= d <= 8, indicating the direction of the next
 *  higher numbered square in the solution: d * 45 degrees clockwise
 *  from straight up (i.e., toward higher y coordinates).  Thus,
 *  direction 1 is "northeast", 2 is "east", ..., and 8 is "north".
 *  The highest-numbered square has direction 0.  Certain squares can
 *  have their values <i>fixed</i> to those in the solution.
 *  Initially, the only two squares with fixed values are those with
 *  the lowest and highest sequence numbers in the solution.  Before
 *  the puzzle is presented to the user, however, the program fixes
 *  other numbers so as to make the solution unique for the given
 *  arrows.
 *
 *  At any given time after initialization, a square whose value is
 *  not fixed may have an unknown value, represented as 0, or a
 *  tentative number (not necessarily that of the solution) between 1
 *  and size(). Squares may be connected together, indicating that
 *  their sequence numbers (unknown or not) are consecutive.
 *
 *  When square S0 is connected to S1, we say that S1 is the
 *  <i>successor</i> of S0, and S0 is the <i>predecessor</i> of S1.
 *  Sequences of connected squares with as-yet unknown values (denoted
 *  by 0) form a <i>group</i>, identified by a unique <i>group
 *  number</i>.  Numbered cells (whether linked or not) are in group
 *  0.  Unnumbered, unlinked cells are in group -1.  On the board displayed
 *  to the user, cells in the same group indicate their grouping and sequence
 *  with labels such as "a", "a+1", "a+2", etc., with a different letter
 *  for each different group.  The first square in a group is called the
 *  <i>head</i> of the group.  Each unnumbered square points to the head
 *  of its group (if the square is unlinked, it points to itself).
 *
 *  Squares are represented as objects of the inner class Sq
 *  (Model.Sq).  A Model object is itself iterable. That is, if M is
 *  of type Model, one can write
 *       for (Model.Sq s : M) { ... }
 *  to sequence through all its squares in unspecified order.
 *
 *  The puzzle is solved when all cells are contained in a single
 *  sequence of consecutively numbered cells (therefore all in group
 *  0) and all cells with fixed sequence numbers appear at the
 *  corresponding position in that sequence.
 *
 *  @author
 */
class Model implements Iterable<Model.Sq> {


    /** A Model whose solution is SOLUTION, initialized to its
     *  starting, unsolved state (where only cells with fixed numbers
     *  currently have sequence numbers and no unnumbered cells are
     *  connected).  SOLUTION must be a proper solution:
     *    1. It must have dimensions w x h such that w * h >= 2.
     *    2. There must be a sequence of chess-queen moves such that the
     *       sequence of values in the cells reached is 1, 2, ... w * h.
     *       The contents of SOLUTION are copied into a fresh array in
     *       this Model, so that subsequent changes to SOLUTION have no
     *       effect on the Model.  */
    Model(int[][] solution) {
        if (solution.length == 0 || solution.length * solution[0].length < 2) {
            throw badArgs("must have at least 2 squares");
        }
        _width = solution.length; _height = solution[0].length;
        int last = _width * _height;
        BitSet allNums = new BitSet();

        _allSuccessors = Place.successorCells(_width, _height);
        _solution = new int[_width][_height];
        deepCopy(solution, _solution);



        _board = new Sq[_width][_height];
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                if (_solution[row][col] == 1 || _solution[row][col] == last) {
                    _board[row][col] = new Sq(row, col, _solution[row][col], true, arrowDirection(row, col), 0);
                } else {
                    _board[row][col] = new Sq(row, col, 0, false, arrowDirection(row, col), -1);
                }
            }
        }



        _allSquares = new ArrayList<>();
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                    _allSquares.add(_board[row][col]);}}


        _solnNumToPlace = new Place[last+1];
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                for (int i=1; i<=last; i+=1){
                    if (_solution[row][col]==i) _solnNumToPlace[i] = _board[row][col].pl;
                }}}
        for (int j =1; j< _solnNumToPlace.length;j+=1) {
            if (_solnNumToPlace[j] ==null) throw badArgs("not all 1-last numbers appear");
        }
        // second part:
        //for (int row = 0; row < _width; row+=1){
          //  for (int col = 0; col < _height; col+=1) {
            //    _board[row][col]._successors = _allSuccessors[row][col][_board[row][col]._dir];
            //}
            //}
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                if (_board[row][col].direction()!=0) {
                    _board[row][col]._successors=_allSuccessors[row][col][_board[row][col].direction()];
                }
                _board[row][col]._successors = new PlaceList();

                for (Place pl : _board[row][col].successors()){
                    if (get(pl)._predecessors ==null) get(pl)._predecessors = new PlaceList();
                    _board[pl.x][pl.y]._predecessors.add(_board[row][col].pl);
                }
            }
        }


        _unconnected = last - 1;
    }

    /** Initializes a copy of MODEL. */
    Model(Model model) {
        _width = model.width(); _height = model.height();
        _unconnected = model._unconnected;
        _solnNumToPlace = model._solnNumToPlace;
        _solution = model._solution;
        _usedGroups.addAll(model._usedGroups);
        _allSuccessors = model._allSuccessors;
        _board = new Sq[_width][_height];
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                _board[row][col] = new Sq(row, col, model.get(row, col).sequenceNum(),model.get(row, col).hasFixedNum(), model.get(row, col).direction(), model.get(row, col).group());
            }
        }
        _allSquares = new ArrayList<>();
        for (int row = 0; row < _width; row+=1){
            for (int col = 0; col < _height; col+=1) {
                _allSquares.add(_board[row][col]);
            }
            }
        for (Sq sq: _allSquares) {
            Sq new_sq = model.get(sq.pl);
            sq._successors=new_sq._successors;
            sq._predecessors = new_sq._predecessors;
            if (new_sq._head!=null) sq._head = this.get(new_sq._head.pl);
            if (new_sq._successor !=null) sq._successor = this.get(new_sq._successor.pl);
            if (new_sq._predecessor!=null) sq._predecessor=this.get(new_sq._predecessor.pl);
        }


        // FIXME: Initialize _board and _allSquares to contain copies of the
        //        the Sq objects in MODEL other than their _successor,
        //        _predecessor, and _head fields (which can't necessarily be
        //        set until all the Sq objects are first created.)

        // FIXME: Once all the new Sq objects are in place, fill in their
        //        _successor, _predecessor, and _head fields.  For example,
        //        if in MODEL, the _successor field of the Sq at
        //        position (2, 3) pointed to the Sq in MODEL at position
        //        (4, 1), then the Sq at position (2, 3) in this copy
        //        will have a _successor field pointing to the Sq at
        //        position (4, 1) in this copy.  Be careful NOT to have
        //        any of these fields in the copy pointing at the old Sqs in
        //        MODEL.
    }

    /** Returns the width (number of columns of cells) of the board. */
    final int width() {
        return _width;
    }

    /** Returns the height (number of rows of cells) of the board. */
    final int height() {
        return _height;
    }

    /** Returns the number of cells (and thus, the sequence number of the
     *  final cell). */
    final int size() {
        return _width * _height;
    }

    /** Returns true iff (X, Y) is a valid cell location. */
    final boolean isCell(int x, int y) {
        return 0 <= x && x < width() && 0 <= y && y < height();
    }

    /** Returns true iff P is a valid cell location. */
    final boolean isCell(Place p) {
        return isCell(p.x, p.y);
    }

    /** Returns all cell locations that are a queen move from (X, Y)
     *  in direction DIR, or all queen moves in any direction if DIR = 0. */
    final PlaceList allSuccessors(int x, int y, int dir) {
        return _allSuccessors[x][y][dir];
    }

    /** Returns all cell locations that are a queen move from P in direction
     *  DIR, or all queen moves in any direction if DIR = 0. */
    final PlaceList allSuccessors(Place p, int dir) {
        return _allSuccessors[p.x][p.y][dir];
    }

    /** Remove all connections and non-fixed sequence numbers. */
    void restart() {
        for (Sq sq : this) {
            sq.disconnect();
        }
        assert _unconnected == _width * _height - 1;
    }

    /** Return the number array that solves the current puzzle (the argument
     *  the constructor.  The result must not be subsequently modified.  */
    final int[][] solution() {
        return _solution;
    }

    /** Return the position of the cell with sequence number N in this board's
     *  solution. */
    Place solnNumToPlace(int n) {
        return _solnNumToPlace[n];
    }

    /** Return the Sq with sequence number N in this board's solution. */
    Sq solnNumToSq(int n) {
        return get(solnNumToPlace(n));
    }

    /** Return the current number of unconnected cells. */
    final int unconnected() {
        return _unconnected;
    }

    /** Returns true iff the puzzle is solved. */
    final boolean solved() {
        return _unconnected == 0;
    }

    /** Return the cell at (X, Y). */
    final Sq get(int x, int y) {
        return _board[x][y];
    }

    /** Return the cell at P. */
    final Sq get(Place p) {
        return p == null ? null : _board[p.x][p.y];
    }

    /** Return the cell at the same position as SQ (generally from another
     *  board), or null if SQ is null. */
    final Sq get(Sq sq) {
        return sq == null ? null : _board[sq.x][sq.y];
    }

    /** Connect all numbered cells with successive numbers that as yet are
     *  unconnected and are separated by a queen move.  Returns true iff
     *  any changes were made. */
    boolean autoconnect() {
        boolean returnedVal = false;
        for (int row=0; row<_allSquares.size(); row+=1){
            for (int col=0; col<_allSquares.size(); col+=1){
                if (_allSquares.get(row).sequenceNum()!=0 && _allSquares.get(col).sequenceNum()!=0){
                    if (_allSquares.get(row).connectable(_allSquares.get(col))) {
                        _allSquares.get(row).connect(_allSquares.get(col));
                        returnedVal = true;
                    }
                }
            }
        }
        return returnedVal;
    }

    /** Sets the numbers in this board's squares to the solution from which
     *  this board was last initialized by the constructor. */
    void solve() {
        for (int row = 1; row < size(); row+=1) {
            get(solnNumToPlace(row)).disconnect();
        }

        for (int col = 1; col < size(); col+=1) {
            if (get(solnNumToSq(col)).connectable(get(solnNumToSq(col+1))))
                get(solnNumToSq(col)).connect(get(solnNumToSq(col+1)));
            }


        _unconnected = 0;
    }

    /** Return the direction from cell (X, Y) in the solution to its
     *  successor, or 0 if it has none. */
   /* public static void main(String []args){
        Model model = new Model(new int[][] {{1,2,3},
                                                {4,5,6},
                                                    {7,8,9} });
        System.out.println(model.arrowDirection(0,1));
    }*/

    private int arrowDirection(int x, int y) {
        int seq0 = _solution[x][y];
        if (seq0 >= _width * _height) return 0;
        for (int row = 0; row < _board.length; row++) {
            for (int col = 0; col < _board[0].length; col++) {
                if (_solution[row][col] == (seq0 + 1)) return Place.dirOf(x, y, row, col);
            }
        }
        return 0;
    }
        //Place newidk =_solnNumToPlace[seq0+1];
        //System.out.println(newidk);
        //return dirOf( x,y, _solnNumToPlace[seq0+1].x, _solnNumToPlace[seq0+1].y);


    /** Return a new, currently unused group number > 0.  Selects the
     *  lowest not currently in use. */
    private int newGroup() {
        for (int i = 1; true; i += 1) {
            if (_usedGroups.add(i)) {
                return i;
            }
        }
    }

    /** Indicate that group number GROUP is no longer in use. */
    private void releaseGroup(int group) {
        _usedGroups.remove(group);
    }

    /** Combine the groups G1 and G2, returning the resulting group. Assumes
     *  G1 != 0 != G2 and G1 != G2. */
    private int joinGroups(int g1, int g2) {
        assert (g1 != 0 && g2 != 0);
        if (g1 == -1 && g2 == -1) {
            return newGroup();
        } else if (g1 == -1) {
            return g2;
        } else if (g2 == -1) {
            return g1;
        } else if (g1 < g2) {
            releaseGroup(g2);
            return g1;
        } else {
            releaseGroup(g1);
            return g2;
        }
    }

    @Override
    public Iterator<Sq> iterator() {
        return _allSquares.iterator();
    }

    @Override
    public String toString() {
        String hline;
        hline = "+";
        for (int x = 0; x < _width; x += 1) {
            hline += "------+";
        }

        Formatter out = new Formatter();
        for (int y = _height - 1; y >= 0; y -= 1) {
            out.format("%s%n", hline);
            out.format("|");
            for (int x = 0; x < _width; x += 1) {
                Sq sq = get(x, y);
                if (sq.hasFixedNum()) {
                    out.format("+%-5s|", sq.seqText());
                } else {
                    out.format("%-6s|", sq.seqText());
                }
            }
            out.format("%n|");
            for (int x = 0; x < _width; x += 1) {
                Sq sq = get(x, y);
                if (sq.predecessor() == null && sq.sequenceNum() != 1) {
                    out.format(".");
                } else {
                    out.format(" ");
                }
                if (sq.successor() == null
                    && sq.sequenceNum() != size()) {
                    out.format("o ");
                } else {
                    out.format("  ");
                }
                out.format("%s |", ARROWS[sq.direction()]);
            }
            out.format("%n");
        }
        out.format(hline);
        return out.toString();
    }

    @Override
    public boolean equals(Object obj) {
        Model model = (Model) obj;
        return (_unconnected == model._unconnected
                && _width == model._width && _height == model._height
                && Arrays.deepEquals(_solution, model._solution)
                && Arrays.deepEquals(_board, model._board));
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_solution) * Arrays.deepHashCode(_board);
    }

    /** Represents a square on the board. */
    final class Sq {
        /** A square at (X0, Y0) with arrow in direction DIR (0 if not
         *  set), group number GROUP, sequence number SEQUENCENUM (0
         *  if none initially assigned), which is fixed iff FIXED. */
        Sq(int x0, int y0, int sequenceNum, boolean fixed, int dir, int group) {
            x = x0; y = y0;
            pl = pl(x, y);
            _hasFixedNum = fixed;
            _sequenceNum = sequenceNum;
            _dir = dir;
            _head = this;
            _group = group;
        }

        /** A copy of OTHER, excluding head, successor, and predecessor. */
        Sq(Sq other) {
            this(other.x, other.y, other._sequenceNum, other._hasFixedNum,
                 other._dir, other._group);
            _successor = _predecessor = null;
            _head = this;
            _successors = other._successors;
            _predecessors = other._predecessors;
        }

        /** Return this square's current sequence number, or 0 if
         *  none assigned. */
        int sequenceNum() {
            return _sequenceNum;
        }

        /** Fix this square's current sequence number at N>0.  It is
         *  an error if this square's number is not initially 0 or N. */
        void setFixedNum(int n) {
            if (n == 0 || (_sequenceNum != 0 && _sequenceNum != n)) {
                throw badArgs("sequence number may not be fixed");
            }
            _hasFixedNum = true;
            if (_sequenceNum == n) {
                return;
            } else {
                releaseGroup(_head._group);
            }
            _sequenceNum = n;
            for (Sq sq = this; sq._successor != null; sq = sq._successor) {
                sq._successor._sequenceNum = sq._sequenceNum + 1;
            }
            for (Sq sq = this; sq._predecessor != null; sq = sq._predecessor) {
                sq._predecessor._sequenceNum = sq._sequenceNum - 1;
            }
        }

        /** Unfix this square's sequence number if it is currently fixed;
         *  otherwise do nothing. */
        void unfixNum() {
            Sq next = _successor, pred = _predecessor;
            _hasFixedNum = false;
            disconnect();
            if (pred != null) {
                pred.disconnect();
            }
            _sequenceNum = 0;
            if (next != null) {
                connect(next);
            }
            if (pred != null) {
                pred.connect(this);
            }
        }

        /** Return true iff this square's sequence number is fixed. */
        boolean hasFixedNum() {
            return _hasFixedNum;
        }

        /** Returns direction of this square's arrow (0 if no arrow). */
        int direction() {
            return _dir;
        }

        /** Return this square's current predecessor. */
        Sq predecessor() {
            return _predecessor;
        }

        /** Return this square's current successor. */
        Sq successor() {
            return _successor;
        }

        /** Return the head of the connected sequence this square
         * is currently in. */
        Sq head() {
            return _head;
        }

        /** Return the group number of this square's group.  It is
         *  0 if this square is numbered, and-1 if it is alone in its group. */
        int group() {
            if (_sequenceNum != 0) {
                return 0;
            } else {
                return _head._group;
            }
        }

        /** Size of alphabet. */
        static final int ALPHA_SIZE = 26;

        /** Return a textual representation of this square's sequence number or
         *  group/position. */
        String seqText() {
            if (_sequenceNum != 0) {
                return String.format("%d", _sequenceNum);
            }
            int g = group() - 1;
            if (g < 0) {
                return "";
            }

            String groupName =
                String.format("%s%s",
                              g < ALPHA_SIZE ? ""
                              : Character.toString((char) (g / ALPHA_SIZE
                                                           + 'a')),
                              Character.toString((char) (g % ALPHA_SIZE
                                                         + 'a')));
            if (this == _head) {
                return groupName;
            }
            int n;
            n = 0;
            for (Sq p = this; p != _head; p = p._predecessor) {
                n += 1;
            }
            return String.format("%s%+d", groupName, n);
        }

        /** Return locations of this square's potential successors. */
        PlaceList successors() {
            return _successors;
        }

        /** Return locations of this square's potential predecessors. */
        PlaceList predecessors() {
            return _predecessors;
        }

        /** Returns true iff this square may be connected to square S1, that is:
         *  + S1 is in the correct direction from this square.
         *  + S1 does not have a current predecessor, this square does not
         *    have a current successor, S1 is not the first cell in sequence,
         *    and this square is not the last.
         *  + If S1 and this square both have sequence numbers, then
         *    this square's is sequenceNum() == S1.sequenceNum() - 1.
         *  + If neither S1 nor this square have sequence numbers, then
         *    they are not part of the same connected sequence.
         */
        boolean connectable(Sq s1) {
            if (s1._predecessor == null) {
                if (this._successor == null) {
                    if (s1._sequenceNum !=1) {
                        if (this._sequenceNum != _width*_height) {
                            if (this._dir == dirOf( this.x, this.y, s1.x, s1.y)) {
                                if (this._sequenceNum!=0 && s1._sequenceNum!=0) {
                                        if (this._sequenceNum == s1._sequenceNum-1) return true;
                                        else return false;
                                }
                                else if (this._sequenceNum == 0 && s1._sequenceNum == 0){
                                    if ((this._group ==s1._group) ) return true;
                                    else return false;
                                }
                                else return true;

                            }
                        }

                    }

                }
            }
            return false;
        }

        /** Connect this square to S1, if both are connectable; otherwise do
         *  nothing. Returns true iff this square and S1 were connectable.
         *  Assumes S1 is in the proper arrow direction from this square. */
        boolean connect(Sq s1) {
            if (!connectable(s1)) {
                return false;
            }
            int sgroup = s1.group();

            _unconnected -= 1;
            _successor = s1;
            s1._predecessor = this;

            if (_sequenceNum !=0) {
                for (Sq sq = this; sq._successor != null; sq = sq._successor) {
                    sq._successor._sequenceNum = sq._sequenceNum + 1;

                }}
            if (s1._sequenceNum !=0) {
                this._sequenceNum = s1._sequenceNum -1;
                for (Sq sq = this; sq._predecessor != null; sq = sq._predecessor) {
                    sq._predecessor._sequenceNum = sq._sequenceNum - 1;

                }
            }
            for (Sq sq = this; sq._successor!=null; sq=sq._successor) {
                sq._successor._head = this._head;
            }
            for (Sq sq = this; sq._predecessor != null; sq = sq._predecessor) {
                sq._predecessor._head = this._head;
            }

            if (_sequenceNum == 0 ^ s1._sequenceNum==0) {
                if (_sequenceNum == 0) releaseGroup(_group);
                else releaseGroup(s1._group);
            }
            if (_sequenceNum ==0 && s1._sequenceNum ==0) {
                _head._group = newGroup();
            }

            // FIXME: Connect this square to its successor:
            //        + Set this square's _successor field and S1's
            //          _predecessor field.
            //        + If this square has a number, number all its successors
            //          accordingly (if needed).
            //        + If S1 is numbered, number this square and its
            //          predecessors accordingly (if needed).
            //        + Set the _head fields of this square's successors this
            //          square's _head.
            //        + If either of this square or S1 used to be unnumbered
            //          and is now numbered, release its group of whichever
            //          was unnumbered, so that it can be reused.
            //        + If both this square and S1 are unnumbered, set the
            //          group of this square's head to the result of joining
            //          the two groups.

            return true;
        }
        /** Disconnect this square from its current successor, if any. */
        void disconnect() {
            Sq next = _successor;
            if (next == null) {
                return;
            }
            _unconnected += 1;
            next._predecessor = _successor = null;
            if (_sequenceNum == 0) {
                if (next._successor ==null && this._predecessor==null) {
                    releaseGroup(this._group);
                    this._group =-1;
                    next._group = -1;
                }
                else if (next._successor ==null || this._predecessor==null) {
                    if (next._successor ==null) next._group=-1;
                    else if (this._predecessor ==null) this._group=-1;

                }

                // FIXME: If both this and next are now one-element groups,
                //        release their former group and set both group
                //        numbers to -1.
                //        Otherwise, if either is now a one-element group, set
                //        its group number to -1 without releasing the group
                //        number.
                //        Otherwise, the group has been split into two multi-
                //        element groups.  Create a new group for next.
            } else {
                if (!this._hasFixedNum) {
                    if (this._predecessor == null) {
                        this._sequenceNum = 0;
                        this._group = -1;}
                    else {

                        boolean hasFixed = false;
                        for (Sq sq = this; sq._predecessor != null; sq = sq._predecessor) {
                            if (sq._predecessor._hasFixedNum) hasFixed = true;
                        }
                        if (hasFixed == false) {
                            this._sequenceNum =0;
                            this._group = newGroup();
                            for (Sq sq = this; sq._predecessor != null; sq = sq._predecessor) {
                                sq._predecessor._sequenceNum = 0;
                                sq._predecessor._group = sq._group;
                            }
                        }
                    }
                }
                if (!next._hasFixedNum) {
                    if (next._successor == null) {
                        next._sequenceNum = 0;
                        next._group = -1; }
                    else {


                        boolean hasFixedTwo = false;
                        for (Sq sq = next; sq._successor != null; sq = sq._successor) {
                            if (next._successor._hasFixedNum) hasFixedTwo = true;
                        }
                        if (!hasFixedTwo) {
                            next._sequenceNum = 0;
                            next._group = newGroup();
                            for (Sq sq = next; sq._successor != null; sq = sq._successor) {
                                sq._successor._sequenceNum = 0;
                                sq._successor._group = sq._group;
                            }
                        }
                    }
                }

                // FIXME: If neither this nor any square in its group that
                //        precedes it has a fixed sequence number, set all
                //        their sequence numbers to 0 and create a new group
                //        for them if this has a current predecessor (other
                //        set group to -1).
                // FIXME: If neither next nor any square in its group that
                //        follows it has a fixed sequence number, set all
                //        their sequence numbers to 0 and create a new
                //        group for them if next has a current successor
                //        (otherwise set next's group to -1.)
            }
            next._head = next;
            for (Sq sq = next; sq._successor != null; sq = sq._successor) {
                sq._successor._head = sq._head;

            }
            // FIXME: Set the _head of next and all squares in its group to
            //        next.
        }

        @Override
        public boolean equals(Object obj) {
            Sq sq = (Sq) obj;
            return sq != null
                && pl == sq.pl
                && _hasFixedNum == sq._hasFixedNum
                && _sequenceNum == sq._sequenceNum
                && _dir == sq._dir
                && (_predecessor == null) == (sq._predecessor == null)
                && (_predecessor == null
                    || _predecessor.pl == sq._predecessor.pl)
                && (_successor == null || _successor.pl == sq._successor.pl);
        }

        @Override
        public int hashCode() {
            return (x + 1) * (y + 1) * (_dir + 1)
                * (_hasFixedNum ? 3 : 1) * (_sequenceNum + 1);
        }

        @Override
        public String toString() {
            return String.format("<Sq@%s, dir: %d>", pl, direction());
        }

        /** The coordinates of this square in the board. */
        protected final int x, y;
        /** The coordinates of this square as a Place. */
        protected final Place pl;
        /** The first in the currently connected sequence of cells ("group")
         *  that includes this one. */
        private Sq _head;
        /** The group number of the group of which this is a member, if
         *  head == this.  Numbered sequences have a group number of 0,
         *  regardless of the value of _group. Unnumbered one-member groups
         *  have a group number of -1.  If _head != this and the square is
         *  unnumbered, then _group is undefined and the square's group
         *  number is maintained in _head._group. */
        private int _group;
        /** True iff assigned a fixed sequence number. */
        private boolean _hasFixedNum;
        /** The current imputed or fixed sequence number,
         *  numbering from 1, or 0 if there currently is none. */
        private int _sequenceNum;
        /** The arrow direction. The possible values are 0 (for unset),
         *  1 for northeast, 2 for east, 3 for southeast, 4 for south,
         *  5 for southwest, 6 for west, 7 for northwest, and 8 for north. */
        private int _dir;
        /** The current predecessor of this square, or null if there is
         *  currently no predecessor. */
        private Sq _predecessor;
        /** The current successor of this square, or null if there is
         *  currently no successor. */
        private Sq _successor;
        /** Locations of the possible predecessors of this square. */
        private PlaceList _predecessors;
        /** Locations of the possible successors of this square. */
        private PlaceList _successors;
    }

    /** ASCII denotations of arrows, indexed by direction. */
    private static final String[] ARROWS = {
        " *", "NE", "E ", "SE", "S ", "SW", "W ", "NW", "N "
    };

    /** Number of squares that haven't been connected. */
    private int _unconnected;
    /** Dimensions of board. */
    private int _width, _height;
    /** Contents of board, indexed by position. */
    private Sq[][] _board;
    /** Contents of board as a sequence of squares for convenient iteration. */
    private ArrayList<Sq> _allSquares = new ArrayList<>();
    /** _allSuccessors[x][y][dir] is a sequence of all queen moves possible
     *  on the board of in direction dir from (x, y).  If dir == 0,
     *  this is all places that are a queen move from (x, y) in any
     *  direction. */
    private PlaceList[][][] _allSuccessors;
    /** The solution from which this Model was built. */
    private int[][] _solution;
    /** Inverse mapping from sequence numbers to board positions. */
    private Place[] _solnNumToPlace;
    /** The set of positive group numbers currently in use. */
    private HashSet<Integer> _usedGroups = new HashSet<>();

}
