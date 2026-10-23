//! Chrome's native messaging framing: a 32-bit length in the machine's byte order, then that many bytes of UTF-8
//! JSON. A message to the browser may be at most 1 MiB; one from the browser at most 64 MiB.

use serde_json::Value;
use std::io::{self, Read, Write};

const MAX_OUT: usize = 1024 * 1024;
const MAX_IN: usize = 64 * 1024 * 1024;

/// The next message, or `None` when the browser has closed the pipe.
pub fn read_frame(input: &mut impl Read) -> io::Result<Option<Vec<u8>>> {
    let mut length = [0u8; 4];
    match input.read_exact(&mut length) {
        Ok(()) => {}
        Err(error) if error.kind() == io::ErrorKind::UnexpectedEof => return Ok(None),
        Err(error) => return Err(error),
    }
    let length = u32::from_ne_bytes(length) as usize;
    if length > MAX_IN {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "message too large"));
    }
    let mut body = vec![0u8; length];
    input.read_exact(&mut body)?;
    Ok(Some(body))
}

pub fn write_frame(output: &mut impl Write, message: &Value) -> io::Result<()> {
    let body = serde_json::to_vec(message).map_err(io::Error::other)?;
    if body.len() > MAX_OUT {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "reply too large"));
    }
    output.write_all(&(body.len() as u32).to_ne_bytes())?;
    output.write_all(&body)?;
    output.flush()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn a_frame_round_trips() {
        let mut wire = Vec::new();
        write_frame(&mut wire, &json!({"id": 1, "type": "hello"})).unwrap();
        let mut cursor = io::Cursor::new(wire);
        let body = read_frame(&mut cursor).unwrap().unwrap();
        let value: Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(value["type"], "hello");
        assert!(read_frame(&mut cursor).unwrap().is_none());
    }

    #[test]
    fn an_oversized_frame_is_refused() {
        let wire = (MAX_IN as u32 + 1).to_ne_bytes().to_vec();
        assert!(read_frame(&mut io::Cursor::new(wire)).is_err());
    }
}
