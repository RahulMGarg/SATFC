/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.sat.base;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A SAT formula in Conjunctive Normal Form (a conjunction of clauses - AND's of OR's of literals). Implementation wise just a clause collection wrapper. 
 * @author afrechet
 */
public class CNF implements Collection<Clause>{

    protected final Collection<Clause> clauses;

    public CNF()
    {
        clauses = new ArrayDeque<Clause>();
        //clauses = new HashSet<Clause>();
    }

    /**
     * Builds and returns the <a href="http://fairmut3x.wordpress.com/2011/07/29/cnf-conjunctive-normal-form-dimacs-format-explained/">DIMACS</a> string representation of the CNF.
     * @param aComments - the comments to add at the beginning of the CNF, if any.
     * @return the DIMACS string representation of the CNF.
     */
    public String toDIMACS(String[] aComments)
    {
        StringBuilder aStringBuilder = new StringBuilder();

        int aNumClauses = clauses.size();
        long aMaxVariable = 0;

        for(Clause aClause : clauses)
        {
            ArrayList<String> aLitteralStrings = new ArrayList<String>();

            for(Literal aLitteral : aClause)
            {
                if(aLitteral.getVariable()<=0)
                {
                    throw new IllegalArgumentException("Cannot transform to DIMACS a CNF that has a litteral with variable value <= 0 (clause: "+aClause.toString()+").");
                }
                else if(aLitteral.getVariable()>aMaxVariable)
                {
                    aMaxVariable = aLitteral.getVariable();
                }
                aLitteralStrings.add((aLitteral.getSign() ? "" : "-") + Long.toString(aLitteral.getVariable()));
            }

            aStringBuilder.append(StringUtils.join(aLitteralStrings, " ")).append(" 0\n");
        }

        aStringBuilder.insert(0, "p cnf "+aMaxVariable+" "+aNumClauses+"\n");

        if (aComments != null)
        {
            for(int i=aComments.length-1;i>=0;i--)
            {
                aStringBuilder.insert(0, "c "+aComments[i].trim()+"\n");
            }
        }

        return aStringBuilder.toString();
    }

    /**
     * @return all the variables present in the CNF.
     */
    public Collection<Long> getVariables()
    {
        Collection<Long> aVariables = new HashSet<Long>();

        for(Clause aClause : clauses)
        {
            for(Literal aLitteral : aClause)
            {
                aVariables.add(aLitteral.getVariable());
            }
        }

        return aVariables;
    }

    @Override
    public String toString()
    {
        ArrayDeque<String> aClauseStrings = new ArrayDeque<String>();
        for(Clause aClause : clauses)
        {
            aClauseStrings.add("("+aClause.toString()+")");
        }
        return StringUtils.join(aClauseStrings," ^ ");
    }

    public String getHashString() {
        String aString = this.toString();
        MessageDigest aDigest = DigestUtils.getSha1Digest();
        try {
            byte[] aResult = aDigest.digest(aString.getBytes("UTF-8"));
            String aResultString = new String(Hex.encodeHex(aResult));  
            return aResultString;
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not encode cnf with sha1 hash.", e);
        }
    }

    @Override
    public int size() {
        return clauses.size();
    }

    @Override
    public boolean isEmpty() {
        return clauses.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return clauses.contains(o);
    }

    @Override
    public Iterator<Clause> iterator() {
        return clauses.iterator();
    }

    @Override
    public Object[] toArray() {
        return clauses.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return clauses.toArray(a);
    }

    @Override
    public boolean add(Clause e) {

        if(e==null)
        {
            throw new IllegalArgumentException("Cannot add a null clause to a CNF.");
        }

        return clauses.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return clauses.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return clauses.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Clause> c) {

        if(c.contains(null))
        {
            throw new IllegalArgumentException("Cannot add a null clause to a CNF.");
        }

        return clauses.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return clauses.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return clauses.remove(c);
    }

    @Override
    public void clear() {
        clauses.clear();
    }

}
