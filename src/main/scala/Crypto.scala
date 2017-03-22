package org.nvotes.trustee

import java.security._
import java.io.BufferedInputStream
import javax.xml.bind.DatatypeConverter
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.Files
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.io.InputStream
import java.util.Base64

import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZMod
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModElement
import ch.bfh.unicrypt.helper.converter.classes.ConvertMethod
import ch.bfh.unicrypt.helper.converter.classes.bytearray.BigIntegerToByteArray
import ch.bfh.unicrypt.helper.converter.classes.bytearray.StringToByteArray
import ch.bfh.unicrypt.helper.hash.HashAlgorithm
import ch.bfh.unicrypt.helper.hash.HashMethod
import ch.bfh.unicrypt.helper.math.Alphabet
import ch.bfh.unicrypt.math.algebra.concatenative.classes.ByteArrayMonoid
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringMonoid
import ch.bfh.unicrypt.crypto.schemes.signature.classes.RSASignatureScheme
import ch.bfh.unicrypt.crypto.schemes.encryption.classes.RSAEncryptionScheme
import ch.bfh.unicrypt.crypto.schemes.padding.classes.PKCSPaddingScheme
import ch.bfh.unicrypt.crypto.schemes.encryption.classes.AESEncryptionScheme
import ch.bfh.unicrypt.crypto.schemes.padding.classes.ANSIPaddingScheme
import ch.bfh.unicrypt.crypto.schemes.padding.classes.PKCSPaddingScheme
import ch.bfh.unicrypt.math.algebra.general.classes.FiniteByteArrayElement
import ch.bfh.unicrypt.math.algebra.concatenative.classes.ByteArrayElement
import ch.bfh.unicrypt.helper.array.classes.ByteArray
import ch.bfh.unicrypt.crypto.schemes.encryption.classes.ElGamalEncryptionScheme
import ch.bfh.unicrypt.math.algebra.general.classes.Pair

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.ByteOrder

import org.nvotes.mix._

/** Provides various cryptographic operations */
object Crypto {

	/** These unicrypt settings must be common to sign generating and verifying methods */
	val HASH_METHOD = HashMethod.getInstance(HashAlgorithm.SHA256)
	val CONVERT_METHOD = ConvertMethod.getInstance(BigIntegerToByteArray.getInstance(ByteOrder.BIG_ENDIAN),
			StringToByteArray.getInstance(StandardCharsets.UTF_8))

	/** AES 128-bit unicrypt encryption scheme
	 *
	 *	The unicrypt implementation delegates to javax.security.
	 *  It is unclear at this time whether there are benefits to using keylengths of 256
	 *	FIXME verify that the java aes implementation called by unicrypt is constant time
	 */
	val AES = AESEncryptionScheme.getInstance(AESEncryptionScheme.KeyLength.KEY128, AESEncryptionScheme.Mode.CBC,
			   AESEncryptionScheme.DEFAULT_IV)

	/** Returns the sha512 hash of the given file as a String */
	def sha512(file: Path): String = {
		sha512(Files.newInputStream(file))
	}

	/** Returns the sha512 hash of the given Inputstream as a String */
	def sha512(inputStream: InputStream): String = {
		val sha = MessageDigest.getInstance("SHA-512")
		val in = new BufferedInputStream(inputStream, 32768)
		val din = new DigestInputStream(in, sha)
    while (din.read() != -1){}
		din.close()

    DatatypeConverter.printHexBinary(sha.digest())
	}

	/** Returns the sha512 hash of the given String as a String */
	def sha512(input: String): String = {
		val sha = MessageDigest.getInstance("SHA-512")
		val in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))
		val din = new DigestInputStream(in, sha)
    while (din.read() != -1){}
		din.close()

    DatatypeConverter.printHexBinary(sha.digest())
	}

	/** Returns a RSA signature of the given String as a byte array */
	def sign(content: String, privateKey: RSAPrivateKey): Array[Byte] = {
		val toSign = content.getBytes(StandardCharsets.UTF_8)
		sign(toSign, privateKey)
	}

	/** Returns a RSA signature of the given byte array as a byte array */
	def sign(content: Array[Byte], privateKey: RSAPrivateKey): Array[Byte] = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val toSign = byteSpace.getElement(content)
		val scheme = RSASignatureScheme.getInstance(toSign.getSet(),
			ZMod.getInstance(privateKey.getModulus()), CONVERT_METHOD, HASH_METHOD)
		val privateKeyElement = scheme.getVerificationKeySpace().getElement(privateKey.getPrivateExponent())

		scheme.sign(privateKeyElement, toSign).convertToByteArray.getBytes
	}

	/** Returns true if the given signature byte array and content String is correct */
	def verify(content: String, signature: Array[Byte], publicKey: RSAPublicKey): Boolean = {
		val signed = content.getBytes(StandardCharsets.UTF_8)
		verify(signed, signature, publicKey)
	}

	/** Returns true if the given signature String and content String is correct */
	def verify(content: String, signature: String, publicKey: RSAPublicKey): Boolean = {
		val signed = content.getBytes(StandardCharsets.UTF_8)
		val sig = signature.getBytes(StandardCharsets.UTF_8)

		verify(signed, sig, publicKey)
	}

	/** Returns true if the given signature byte array and content byte array is correct */
	def verify(content: Array[Byte], signature: Array[Byte], publicKey: RSAPublicKey): Boolean = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val signed = byteSpace.getElement(content)
		val scheme = RSASignatureScheme.getInstance(signed.getSet(),
			ZMod.getInstance(publicKey.getModulus()), CONVERT_METHOD, HASH_METHOD)
		val signatureByteArray = ByteArray.getInstance(signature :_*)
		val signatureElement = scheme.getSignatureSpace.getElementFrom(signatureByteArray)
		val publicKeyElement = scheme.getSignatureKeySpace().getElement(publicKey.getPublicExponent())

		scheme.verify(publicKeyElement, signed, signatureElement).isTrue
	}

	/** Returns the AES encryption of the given byte array as a byte array */
	def encryptAES(content: Array[Byte], key: FiniteByteArrayElement): Array[Byte] = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val toEncrypt = byteSpace.getElement(content)
		val pkcs = PKCSPaddingScheme.getInstance(16)
		val paddedMessage = pkcs.pad(toEncrypt)
		val encryptedMessage = AES.encrypt(key, paddedMessage)

		encryptedMessage.convertToByteArray.getBytes
	}

	/** Returns the AES decryption of the given byte array as a byte array */
	def decryptAES(content: Array[Byte], key: FiniteByteArrayElement): Array[Byte] = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val toDecrypt = byteSpace.getElement(content)
		val decryptedMessage = AES.decrypt(key, toDecrypt)
		val pkcs = PKCSPaddingScheme.getInstance(16)
		val unpaddedMessage = pkcs.unpad(decryptedMessage)

		unpaddedMessage.convertToByteArray.getBytes
	}

	/** Returns the AES decryption of the given byte array as a base64 encoded String */
	def encryptAES(content: String, key: FiniteByteArrayElement): String = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val toEncrypt = byteSpace.getElement(content.getBytes(StandardCharsets.UTF_8))
		val pkcs = PKCSPaddingScheme.getInstance(16)
		val paddedMessage = pkcs.pad(toEncrypt)
		val encryptedMessage = AES.encrypt(key, paddedMessage)

		val bytes = encryptedMessage.convertToByteArray.getBytes
		Base64.getEncoder().encodeToString(bytes)
		// content
	}

	/** Returns the AES decryption of the given base64 encoded String as a String */
	def decryptAES(content: String, key: FiniteByteArrayElement): String = {
		val byteSpace = ByteArrayMonoid.getInstance()
		val bytes = Base64.getDecoder().decode(content)
		val toDecrypt = byteSpace.getElement(bytes)
		val decryptedMessage = AES.decrypt(key, toDecrypt)
		val pkcs = PKCSPaddingScheme.getInstance(16)
		val unpaddedMessage = pkcs.unpad(decryptedMessage)

		new String(unpaddedMessage.convertToByteArray.getBytes, StandardCharsets.UTF_8)
		// content
	}

	/** Return the AES key in the given file as a unicrypt object */
	def readAESKey(path: Path): FiniteByteArrayElement = {
		val keyString = IO.asString(path)
		AES.getEncryptionKeySpace.getElementFrom(keyString)
	}

	/** Return a random AES key as a byte array */
	def randomAESKey: Array[Byte] = {
		AES.generateSecretKey().convertToByteArray.getBytes
	}

	/** Return a random AES key as a unicrypt converted String */
	def randomAESKeyString: String = {
		AES.generateSecretKey().convertToString
	}

	/** Return a random AES key as a unicrypt object */
	def randomAESKeyElement: FiniteByteArrayElement = {
		AES.generateSecretKey()
	}

	/** Reads a private RSA key from the given file
	 *
	 *	The file must be in pkcs8 PEM format. Example generating and
	 *  converting commands (the second produces the right file)
	 *
	 *  ssh-keygen -t rsa -b 4096 -f keys/id_rsa -q -N ""
	 *  openssl pkcs8 -topk8 -inform PEM -outform PEM -in keys/id_rsa -out keys/id_rsa.pem -nocrypt
	 *
	 */
	def readPrivateRsa(path: Path): RSAPrivateKey = {
		var pkpem = IO.asString(path)
		readPrivateRsa(pkpem)
	}

	/** Reads a public RSA key from the given file
	 *
	 *	The file must be in PEM format. Example generating and
	 *  converting commands (the second produces the right file)
	 *
	 *  ssh-keygen -t rsa -b 4096 -f keys/id_rsa -q -N ""
	 *  openssl rsa -in keys/id_rsa -pubout > keys/id_rsa.pub.pem
	 *
	 */
	def readPublicRsa(path: Path): RSAPublicKey = {
		val pkpem = IO.asString(path)
		readPublicRsa(pkpem)
	}

	/** Reads a private RSA key from the given String
	 *
	 *	The file must be in pkcs8 PEM format. Example generating and
	 *  converting commands (the second produces the right file)
	 *
	 *  ssh-keygen -t rsa -b 4096 -f keys/id_rsa -q -N ""
	 *  openssl pkcs8 -topk8 -inform PEM -outform PEM -in keys/id_rsa -out keys/id_rsa.pem -nocrypt
	 *
	 */
	def readPrivateRsa(str: String): RSAPrivateKey = {
		var pkpem = str
		pkpem = pkpem.replace("-----BEGIN PRIVATE KEY-----\n", "")
		pkpem = pkpem.replace("-----END PRIVATE KEY-----", "")
		val decoded = Base64.getMimeDecoder().decode(pkpem)
		val spec = new PKCS8EncodedKeySpec(decoded)
		val kf = KeyFactory.getInstance("RSA")

		kf.generatePrivate(spec).asInstanceOf[RSAPrivateKey]
	}

	/** Reads a public RSA key from the given String
	 *
	 *	The file must be in PEM format. Example generating and
	 *  converting commands (the second produces the right file)
	 *
	 *  ssh-keygen -t rsa -b 4096 -f keys/id_rsa -q -N ""
	 *  openssl rsa -in keys/id_rsa -pubout > keys/id_rsa.pub.pem
	 *
	 */
	def readPublicRsa(str: String): RSAPublicKey = {
		var pkpem = str
		pkpem = pkpem.replace("-----BEGIN PUBLIC KEY-----\n", "")
		pkpem = pkpem.replace("-----END PUBLIC KEY-----", "")
		val decoded = Base64.getMimeDecoder().decode(pkpem)
		val spec: X509EncodedKeySpec = new X509EncodedKeySpec(decoded)
		val kf = KeyFactory.getInstance("RSA")

		kf.generatePublic(spec).asInstanceOf[RSAPublicKey]
	}
}

/**	Represents a key maker trustee
 *
 * 	Methods to create shares and partially decrypt votes.
 *  Mixes in the nMix KeyMaker trait.
 */
object KeyMakerTrustee extends KeyMaker {

  /**	Creates a key share
   *
   * 	Returns the key share and proof of knowledge as an nMix EncryptionKeyShareDTO.
   *  Returns the private key part of the share as a unicrypted converted String
   */
  def createKeyShare(id: String, cSettings: CryptoSettings): (EncryptionKeyShareDTO, String) = {

    val (encryptionKeyShareDTO, privateKey) = createShare(id, cSettings)

    (encryptionKeyShareDTO, privateKey)
  }

  /**	Partially decrypt a ciphertext with the private part of a share
   *
   * 	Returns the partial decryption and proof of knowledge as an nMix EncryptionKeyShareDTO.
   */
  def partialDecryption(id: String, votes: Seq[String],
  	privateShare: String, cSettings: CryptoSettings): PartialDecryptionDTO = {

    val elGamal = ElGamalEncryptionScheme.getInstance(cSettings.generator)
    val v = votes.par.map( v => Util.fromString(elGamal.getEncryptionSpace, v).asInstanceOf[Pair]).seq
    val secretKey = cSettings.group.getZModOrder().getElementFrom(privateShare)

    partialDecrypt(v, secretKey, id, cSettings)
  }
}


/**	Represents a shuffling trustee
 *
 * 	Methods to mix votes.
 *  Mixes in the nMix Mixer trait.
 */
object MixerTrustee extends Mixer {

	/**	Shuffle the provided votes
   *
   * 	Returns the shuffle and proof of knowledgeas an nMix ShuffleResultDTO
   */
  def shuffleVotes(votes: Seq[String], publicKey: String, id: String, cSettings: CryptoSettings): ShuffleResultDTO = {
    println("Mixer shuffle..")

    // not using Util.getPublicKeyFromString since we need the scheme below
    val elGamal = ElGamalEncryptionScheme.getInstance(cSettings.generator)
    val keyPairGen = elGamal.getKeyPairGenerator()
    val pk = keyPairGen.getPublicKeySpace().getElementFrom(publicKey)

    println("Convert votes..")

    val vs = votes.par.map( v => Util.fromString(elGamal.getEncryptionSpace, v) ).seq

    println("Mixer creating shuffle..")

    shuffle(Util.tupleFromSeq(vs), pk, cSettings, id)
  }

  // TODO add support for offline phase

  /* def preShuffleVotes(e: Election[_, VotesStopped]) = {
    val elGamal = ElGamalEncryptionScheme.getInstance(e.state.cSettings.generator)
    val keyPairGen = elGamal.getKeyPairGenerator()
    val publicKey = keyPairGen.getPublicKeySpace().getElementFrom(e.state.publicKey)

    preShuffle(e.state.votes.size, publicKey, e.state.cSettings, id)
  } */

  /* def shuffleVotes(e: Election[_, Mixing[_]], preData: PreShuffleData, pdtoFuture: Future[PermutationProofDTO]) = {
    println("Mixer..")
    val elGamal = ElGamalEncryptionScheme.getInstance(e.state.cSettings.generator)
    val keyPairGen = elGamal.getKeyPairGenerator()
    val publicKey = keyPairGen.getPublicKeySpace().getElementFrom(e.state.publicKey)
    println("Convert votes..")

    val votes = e.state match {
      case s: Mixing[_0] => e.state.votes.par.map( v => Util.fromString(elGamal.getEncryptionSpace, v) ).seq
      case _ => e.state.mixes.toList.last.votes.par.map( v => Util.fromString(elGamal.getEncryptionSpace, v) ).seq
    }

    println("Mixer creating shuffle..")

    shuffle(Util.tupleFromSeq(votes), publicKey, e.state.cSettings, id, preData, pdtoFuture)
  }*/
}