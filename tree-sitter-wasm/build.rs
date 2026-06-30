fn main() {
    println!("cargo:rerun-if-changed=src");
    println!("cargo:rerun-if-changed=queries");
    println!("cargo:rerun-if-changed=tests");
    println!("cargo:rerun-if-changed=Cargo.toml");
}
