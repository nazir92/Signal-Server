/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.textsecuregcm.util.ByteArrayAdapter;
import org.whispersystems.textsecuregcm.util.OptionalIdentityKeyAdapter;

public record RegistrationRequest(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = """
                                  The ID of an existing verification session as it appears in a verification session
                                  metadata object. Must be provided if `recoveryPassword` is not provided; must not be
                                  provided if `recoveryPassword` is provided.
                                  """)
                                  String sessionId,

                                  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
                                  @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = """
                                  A base64-encoded registration recovery password. Must be provided if `sessionId` is
                                  not provided; must not be provided if `sessionId` is provided
                                  """)
                                  byte[] recoveryPassword,

                                  @NotNull
                                  @Valid
                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                                  AccountAttributes accountAttributes,

                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = """
                                  If true, indicates that the end user has elected not to transfer data from another
                                  device even though a device transfer is technically possible given the capabilities of
                                  the calling device and the device associated with the existing account (if any). If
                                  false and if a device transfer is technically possible, the registration request will
                                  fail with an HTTP/409 response indicating that the client should prompt the user to
                                  transfer data from an existing device.
                                  """)
                                  boolean skipDeviceTransfer,

                                  @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = """
                                  If true, indicates that this is a request for "atomic" registration. If any properties
                                  needed for atomic account creation are not present, the request will fail. If false,
                                  atomic account creation can still occur, but only if all required fields are present.
                                  """)
                                  boolean requireAtomic,

                                  @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = """
                                  The ACI-associated identity key for the account, encoded as a base64 string. If
                                  provided, an account will be created "atomically," and all other properties needed for
                                  atomic account creation must also be present.
                                  """)
                                  @JsonSerialize(using = OptionalIdentityKeyAdapter.Serializer.class)
                                  @JsonDeserialize(using = OptionalIdentityKeyAdapter.Deserializer.class)
                                  Optional<IdentityKey> aciIdentityKey,

                                  @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = """
                                  The PNI-associated identity key for the account, encoded as a base64 string. If
                                  provided, an account will be created "atomically," and all other properties needed for
                                  atomic account creation must also be present.
                                  """)
                                  @JsonSerialize(using = OptionalIdentityKeyAdapter.Serializer.class)
                                  @JsonDeserialize(using = OptionalIdentityKeyAdapter.Deserializer.class)
                                  Optional<IdentityKey> pniIdentityKey,

                                  @JsonUnwrapped
                                  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
                                  DeviceActivationRequest deviceActivationRequest) implements PhoneVerificationRequest {

  @JsonCreator
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public RegistrationRequest(@JsonProperty("sessionId") String sessionId,
      @JsonProperty("recoveryPassword") byte[] recoveryPassword,
      @JsonProperty("accountAttributes") AccountAttributes accountAttributes,
      @JsonProperty("skipDeviceTransfer") boolean skipDeviceTransfer,
      @JsonProperty("requireAtomic") boolean requireAtomic,
      @JsonProperty("aciIdentityKey") Optional<IdentityKey> aciIdentityKey,
      @JsonProperty("pniIdentityKey") Optional<IdentityKey> pniIdentityKey,
      @JsonProperty("aciSignedPreKey") Optional<@Valid ECSignedPreKey> aciSignedPreKey,
      @JsonProperty("pniSignedPreKey") Optional<@Valid ECSignedPreKey> pniSignedPreKey,
      @JsonProperty("aciPqLastResortPreKey") Optional<@Valid KEMSignedPreKey> aciPqLastResortPreKey,
      @JsonProperty("pniPqLastResortPreKey") Optional<@Valid KEMSignedPreKey> pniPqLastResortPreKey,
      @JsonProperty("apnToken") Optional<@Valid ApnRegistrationId> apnToken,
      @JsonProperty("gcmToken") Optional<@Valid GcmRegistrationId> gcmToken) {

    // This may seem a little verbose, but at the time of writing, Jackson struggles with `@JsonUnwrapped` members in
    // records, and this is a workaround. Please see
    // https://github.com/FasterXML/jackson-databind/issues/3726#issuecomment-1525396869 for additional context.
    this(sessionId, recoveryPassword, accountAttributes, skipDeviceTransfer, requireAtomic, aciIdentityKey, pniIdentityKey,
        new DeviceActivationRequest(aciSignedPreKey, pniSignedPreKey, aciPqLastResortPreKey, pniPqLastResortPreKey, apnToken, gcmToken));
  }

  @AssertTrue
  public boolean isEverySignedKeyValid() {
    return validatePreKeySignature(aciIdentityKey(), deviceActivationRequest().aciSignedPreKey())
        && validatePreKeySignature(pniIdentityKey(), deviceActivationRequest().pniSignedPreKey())
        && validatePreKeySignature(aciIdentityKey(), deviceActivationRequest().aciPqLastResortPreKey())
        && validatePreKeySignature(pniIdentityKey(), deviceActivationRequest().pniPqLastResortPreKey());
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private static boolean validatePreKeySignature(final Optional<IdentityKey> maybeIdentityKey,
      final Optional<? extends SignedPreKey<?>> maybeSignedPreKey) {

    return maybeSignedPreKey.map(signedPreKey -> maybeIdentityKey
            .map(identityKey -> PreKeySignatureValidator.validatePreKeySignatures(identityKey, List.of(signedPreKey)))
            .orElse(false))
        .orElse(true);
  }

  @AssertTrue
  public boolean isCompleteRequest() {
    final boolean hasNoAtomicAccountCreationParameters =
        aciIdentityKey().isEmpty()
            && pniIdentityKey().isEmpty()
            && deviceActivationRequest().aciSignedPreKey().isEmpty()
            && deviceActivationRequest().pniSignedPreKey().isEmpty()
            && deviceActivationRequest().aciPqLastResortPreKey().isEmpty()
            && deviceActivationRequest().pniPqLastResortPreKey().isEmpty();

    return supportsAtomicAccountCreation() || (!requireAtomic() && hasNoAtomicAccountCreationParameters);
  }

  public boolean supportsAtomicAccountCreation() {
    return hasExactlyOneMessageDeliveryChannel()
        && aciIdentityKey().isPresent()
        && pniIdentityKey().isPresent()
        && deviceActivationRequest().aciSignedPreKey().isPresent()
        && deviceActivationRequest().pniSignedPreKey().isPresent()
        && deviceActivationRequest().aciPqLastResortPreKey().isPresent()
        && deviceActivationRequest().pniPqLastResortPreKey().isPresent();
  }

  @VisibleForTesting
  boolean hasExactlyOneMessageDeliveryChannel() {
    if (accountAttributes.getFetchesMessages()) {
      return deviceActivationRequest().apnToken().isEmpty() && deviceActivationRequest().gcmToken().isEmpty();
    } else {
      return deviceActivationRequest().apnToken().isPresent() ^ deviceActivationRequest().gcmToken().isPresent();
    }
  }
}
