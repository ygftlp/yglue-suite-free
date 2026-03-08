ALTER TABLE yglue_flow_entrypoint
    ADD COLUMN inbound_interceptors_json TEXT NULL AFTER data_response_format;

