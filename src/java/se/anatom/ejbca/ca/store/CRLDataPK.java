/*
 * Generated by XDoclet - Do not edit!
 */
package se.anatom.ejbca.ca.store;

/**
 * Primary key for CRLData.
 */
public class CRLDataPK
   extends java.lang.Object
   implements java.io.Serializable
{

   public java.lang.String fingerprint;

   public CRLDataPK()
   {
   }

   public CRLDataPK( java.lang.String fingerprint )
   {
      this.fingerprint = fingerprint;
   }

   public java.lang.String getFingerprint()
   {
      return fingerprint;
   }

   public void setFingerprint(java.lang.String fingerprint)
   {
      this.fingerprint = fingerprint;
   }

   public int hashCode()
   {
      int _hashCode = 0;
         if (this.fingerprint != null) _hashCode += this.fingerprint.hashCode();

      return _hashCode;
   }

   public boolean equals(Object obj)
   {
      if( !(obj instanceof se.anatom.ejbca.ca.store.CRLDataPK) )
         return false;

      se.anatom.ejbca.ca.store.CRLDataPK pk = (se.anatom.ejbca.ca.store.CRLDataPK)obj;
      boolean eq = true;

      if( obj == null )
      {
         eq = false;
      }
      else
      {
         if( this.fingerprint != null )
         {
            eq = eq && this.fingerprint.equals( pk.getFingerprint() );
         }
         else  // this.fingerprint == null
         {
            eq = eq && ( pk.getFingerprint() == null );
         }
      }

      return eq;
   }

   /** @return String representation of this pk in the form of [.field1.field2.field3]. */
   public String toString()
   {
      StringBuffer toStringValue = new StringBuffer("[.");
         toStringValue.append(this.fingerprint).append('.');
      toStringValue.append(']');
      return toStringValue.toString();
   }

}
