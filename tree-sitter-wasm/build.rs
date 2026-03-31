fn main() {
    println!("cargo:rerun-if-changed=src/lib.rs");
    println!("cargo:rerun-if-changed=src/utils.rs");
    println!("cargo:rerun-if-changed=Cargo.toml");
}
