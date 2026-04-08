pub struct I128(i128);

impl trait_::Add for I128 {
    fn add(self, other: Self) -> Self {
        I128(self.0 + other.0)
    }
}